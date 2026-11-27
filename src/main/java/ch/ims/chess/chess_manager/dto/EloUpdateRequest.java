package ch.ims.chess.chess_manager.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record EloUpdateRequest(@NotNull @Min(0) Integer currentElo) {}
