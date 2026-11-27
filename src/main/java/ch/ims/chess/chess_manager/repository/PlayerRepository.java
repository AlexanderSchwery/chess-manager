package ch.ims.chess.chess_manager.repository;

import ch.ims.chess.chess_manager.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerRepository extends JpaRepository<Player, Long> {
    List<Player> findByNameContainingIgnoreCaseOrSsbNumberContainingIgnoreCase(String name, String ssbNumber);
}
