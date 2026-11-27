package ch.ims.chess.chess_manager.service;

import ch.ims.chess.chess_manager.dto.MatchCreateRequest;
import ch.ims.chess.chess_manager.dto.MatchResponse;
import ch.ims.chess.chess_manager.exception.BusinessRuleException;
import ch.ims.chess.chess_manager.exception.ResourceNotFoundException;
import ch.ims.chess.chess_manager.model.*;
import ch.ims.chess.chess_manager.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MatchService {

    private final MatchRepository matchRepository;
    private final TournamentService tournamentService;
    private final PlayerService playerService;
    private final EloService eloService;

    @Transactional(readOnly = true)
    public List<MatchResponse> list(Long tournamentId, Integer round, MatchStatus status) {
        tournamentService.find(tournamentId);
        List<Match> matches;
        if (round != null && status != null) {
            matches = matchRepository.findByTournamentIdAndRoundNumberAndStatus(tournamentId, round, status);
        } else if (round != null) {
            matches = matchRepository.findByTournamentIdAndRoundNumber(tournamentId, round);
        } else if (status != null) {
            matches = matchRepository.findByTournamentIdAndStatus(tournamentId, status);
        } else {
            matches = matchRepository.findByTournamentId(tournamentId);
        }
        return matches.stream()
                .sorted(Comparator.comparing(Match::getRoundNumber).thenComparing(Match::getId))
                .map(MatchResponse::from).toList();
    }

    /** Manual ad-hoc match for any round. */
    public MatchResponse create(Long tournamentId, MatchCreateRequest req) {
        Tournament t = tournamentService.find(tournamentId);
        tournamentService.requireActive(t);
        if (t.getDisqualified().contains(req.whitePlayerId()) || t.getDisqualified().contains(req.blackPlayerId())) {
            throw new BusinessRuleException("A disqualified player cannot be paired");
        }
        if (req.whitePlayerId().equals(req.blackPlayerId())) {
            throw new BusinessRuleException("A player cannot play against themselves");
        }
        Player white = participant(t, req.whitePlayerId());
        Player black = participant(t, req.blackPlayerId());

        Match m = new Match();
        m.setTournament(t);
        m.setRoundNumber(req.roundNumber());
        m.setWhitePlayer(white);
        m.setBlackPlayer(black);
        matchRepository.save(m);

        if (t.getStatus() != TournamentStatus.IN_PROGRESS) t.setStatus(TournamentStatus.IN_PROGRESS);
        if (req.roundNumber() > t.getCurrentRound()) t.setCurrentRound(req.roundNumber());
        return MatchResponse.from(m);
    }

    public MatchResponse recordResult(Long matchId, MatchResult result) {
        Match m = find(matchId);
        tournamentService.requireActive(m.getTournament());
        if (result == MatchResult.PENDING || result == MatchResult.BYE) {
            throw new BusinessRuleException(result + " is not a valid result to record");
        }
        if (m.getStatus() != MatchStatus.SCHEDULED) {
            throw new BusinessRuleException("Only scheduled matches accept a result (status is " + m.getStatus() + ")");
        }
        eloService.applyResult(m, result);
        m.setResult(result);
        m.setStatus(MatchStatus.COMPLETED);
        tournamentService.refreshTournamentStatus(m.getTournament());
        return MatchResponse.from(m);
    }

    public MatchResponse cancel(Long matchId) {
        Match m = find(matchId);
        tournamentService.requireActive(m.getTournament());
        if (m.getStatus() != MatchStatus.SCHEDULED) {
            throw new BusinessRuleException("Only scheduled matches can be cancelled (status is " + m.getStatus() + ")");
        }
        m.setStatus(MatchStatus.CANCELLED);
        tournamentService.refreshTournamentStatus(m.getTournament());
        return MatchResponse.from(m);
    }

    private Player participant(Tournament t, Long playerId) {
        Player p = playerService.find(playerId);
        if (!t.getParticipants().contains(p)) {
            throw new BusinessRuleException("Player " + p.getName() + " is not enrolled in this tournament");
        }
        return p;
    }

    private Match find(Long id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Match " + id + " not found"));
    }
}
