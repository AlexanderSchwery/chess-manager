package ch.ims.chess.chess_manager.dto;

import ch.ims.chess.chess_manager.model.TournamentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** totalRounds is optional: derived automatically for round-robin and (by default) Swiss. */
public record TournamentCreateRequest(
        @NotBlank String name,
        @NotNull TournamentType type,
        @Min(1) Integer totalRounds) {}
