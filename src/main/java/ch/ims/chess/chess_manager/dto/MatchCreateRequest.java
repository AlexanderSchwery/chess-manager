package ch.ims.chess.chess_manager.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MatchCreateRequest(
        @NotNull @Min(1) Integer roundNumber,
        @NotNull Long whitePlayerId,
        @NotNull Long blackPlayerId) {}
