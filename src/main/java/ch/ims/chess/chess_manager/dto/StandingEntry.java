package ch.ims.chess.chess_manager.dto;

/**
 * One row of the standings.
 * Tie-breaks: Swiss / free play: points, Buchholz, Sonneborn-Berger, Elo.
 * Double round robin: points, Sonneborn-Berger, Elo.
 */
public record StandingEntry(int rank, Long playerId, String name, double points, int gamesPlayed, int elo,
                            double adjustment, String adjustmentNote, int byes, boolean disqualified,
                            double buchholz, double sonnebornBerger) {}
