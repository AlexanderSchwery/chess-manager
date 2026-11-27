package ch.ims.chess.chess_manager.service;

import ch.ims.chess.chess_manager.dto.PlayerHistory;
import ch.ims.chess.chess_manager.dto.PlayerHistory.GameEntry;
import ch.ims.chess.chess_manager.dto.PlayerHistory.TournamentEntry;
import ch.ims.chess.chess_manager.dto.PlayerResponse;
import ch.ims.chess.chess_manager.dto.StandingEntry;
import ch.ims.chess.chess_manager.model.*;
import ch.ims.chess.chess_manager.repository.MatchRepository;
import ch.ims.chess.chess_manager.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistoryService {

    private final PlayerService playerService;
    private final TournamentRepository tournamentRepository;
    private final MatchRepository matchRepository;
    private final TournamentService tournamentService;

    public PlayerHistory history(Long playerId) {
        Player player = playerService.find(playerId);
        List<TournamentEntry> entries = new ArrayList<>();
        for (Tournament t : tournamentRepository.findAll(Sort.by("id").descending())) {
            List<Match> own = matchRepository.findByTournamentId(t.getId()).stream()
                    .filter(m -> m.getWhitePlayer().getId().equals(playerId)
                            || (m.getBlackPlayer() != null && m.getBlackPlayer().getId().equals(playerId)))
                    .sorted(Comparator.comparing(Match::getRoundNumber).thenComparing(Match::getId))
                    .toList();
            boolean enrolled = t.getParticipants().contains(player);
            if (!enrolled && own.isEmpty()) continue;

            List<StandingEntry> standings = tournamentService.standings(t.getId());
            StandingEntry mine = standings.stream().filter(s -> s.playerId().equals(playerId)).findFirst().orElse(null);
            List<GameEntry> games = own.stream().map(m -> toGame(m, playerId)).toList();
            entries.add(new TournamentEntry(t.getId(), t.getName(), t.getType(), t.getStatus(), t.isArchived(),
                    mine == null ? null : mine.rank(), standings.size(), mine == null ? 0 : mine.points(),
                    t.getDisqualified().contains(playerId), games));
        }
        return new PlayerHistory(PlayerResponse.from(player), entries);
    }

    private GameEntry toGame(Match m, Long playerId) {
        boolean white = m.getWhitePlayer().getId().equals(playerId);
        Player opp = white ? m.getBlackPlayer() : m.getWhitePlayer();
        String outcome;
        if (m.getStatus() == MatchStatus.CANCELLED) outcome = "CANCELLED";
        else if (m.getStatus() == MatchStatus.SCHEDULED) outcome = "SCHEDULED";
        else if (m.isBye()) outcome = "BYE";
        else if (m.getResult() == MatchResult.DRAW) outcome = "DRAW";
        else outcome = (m.getResult() == MatchResult.WHITE_WINS) == white ? "WIN" : "LOSS";
        return new GameEntry(m.getId(), m.getRoundNumber(), opp == null ? null : opp.getName(),
                m.isBye() ? null : (white ? "WHITE" : "BLACK"), outcome,
                white ? m.getWhiteEloBefore() : m.getBlackEloBefore(),
                white ? m.getWhiteEloChange() : m.getBlackEloChange());
    }
}
