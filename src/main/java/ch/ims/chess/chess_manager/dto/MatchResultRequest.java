package ch.ims.chess.chess_manager.dto;

import ch.ims.chess.chess_manager.model.MatchResult;
import jakarta.validation.constraints.NotNull;

public record MatchResultRequest(@NotNull MatchResult result) {}
