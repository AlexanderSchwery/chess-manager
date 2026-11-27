package ch.ims.chess.chess_manager.repository;

import ch.ims.chess.chess_manager.model.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {
}
