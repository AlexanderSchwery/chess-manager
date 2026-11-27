package ch.ims.chess.chess_manager.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlayerCreateRequest(
        @NotBlank String name,
        @NotNull @Min(0) Integer currentElo,
        String ssbNumber) {}
