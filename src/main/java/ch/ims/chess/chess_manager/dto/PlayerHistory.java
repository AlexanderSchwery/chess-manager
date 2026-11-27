package ch.ims.chess.chess_manager.dto;

import ch.ims.chess.chess_manager.model.TournamentStatus;
import ch.ims.chess.chess_manager.model.TournamentType;

import java.util.List;

public record PlayerHistory(PlayerResponse player, List<TournamentEntry> tournaments) {

    public record TournamentEntry(Long tournamentId, String name, TournamentType type, TournamentStatus status,
                                  boolean archived, Integer rank, int participants, double points,
                                  boolean disqualified, List<GameEntry> games) {}

    /** outcome: WIN, DRAW, LOSS, BYE, SCHEDULED, CANCELLED. opponentName is null for a bye. */
    public record GameEntry(Long matchId, int round, String opponentName, String color, String outcome,
                            Integer eloBefore, Integer eloChange) {}
}
