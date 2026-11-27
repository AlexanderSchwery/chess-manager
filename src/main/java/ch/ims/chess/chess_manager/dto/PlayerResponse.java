package ch.ims.chess.chess_manager.dto;

import ch.ims.chess.chess_manager.model.Player;

import java.time.LocalDateTime;

public record PlayerResponse(Long id, String name, String ssbNumber, Integer currentElo, LocalDateTime createdAt) {
    public static PlayerResponse from(Player p) {
        return new PlayerResponse(p.getId(), p.getName(), p.getSsbNumber(), p.getCurrentElo(), p.getCreatedAt());
    }
}
