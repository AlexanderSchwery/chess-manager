package ch.ims.chess.chess_manager.dto;

import ch.ims.chess.chess_manager.model.*;

import java.util.Comparator;
import java.util.List;

public record TournamentResponse(Long id, String name, TournamentType type, Integer totalRounds,
                                 Integer currentRound, TournamentStatus status,
                                 List<PlayerResponse> participants,
                                 List<Long> disqualifiedPlayerIds, boolean archived) {
    public static TournamentResponse from(Tournament t) {
        List<PlayerResponse> players = t.getParticipants().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .map(PlayerResponse::from).toList();
        return new TournamentResponse(t.getId(), t.getName(), t.getType(), t.getTotalRounds(),
                t.getCurrentRound(), t.getStatus(), players,
                List.copyOf(t.getDisqualified()), t.isArchived());
    }
}
