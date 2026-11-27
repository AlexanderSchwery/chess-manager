package ch.ims.chess.chess_manager.dto;

import jakarta.validation.constraints.NotNull;

/** Positive = bonus, negative = penalty. */
public record PointAdjustmentRequest(@NotNull Double points, String reason) {}
