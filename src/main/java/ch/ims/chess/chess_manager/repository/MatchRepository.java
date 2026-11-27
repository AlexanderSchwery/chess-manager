package ch.ims.chess.chess_manager.repository;

import ch.ims.chess.chess_manager.model.Match;
import ch.ims.chess.chess_manager.model.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findByTournamentId(Long tournamentId);
    List<Match> findByTournamentIdAndRoundNumber(Long tournamentId, Integer roundNumber);
    List<Match> findByTournamentIdAndStatus(Long tournamentId, MatchStatus status);
    List<Match> findByTournamentIdAndRoundNumberAndStatus(Long tournamentId, Integer roundNumber, MatchStatus status);
}
