package ch.ims.chess.chess_manager.repository;

import ch.ims.chess.chess_manager.model.PointAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointAdjustmentRepository extends JpaRepository<PointAdjustment, Long> {
    List<PointAdjustment> findByTournamentId(Long tournamentId);
}
