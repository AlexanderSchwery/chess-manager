package ch.ims.chess.chess_manager.dto;

/** One row of the standings. Order: points, then Buchholz (sum of opponents' points), then Elo. */
public record StandingEntry(int rank, Long playerId, String name, double points, int gamesPlayed, int elo,
                            double adjustment, String adjustmentNote, int byes, boolean disqualified,
                            double buchholz) {}
