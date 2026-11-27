package ch.ims.chess.chess_manager.dto;

public record StandingEntry(int rank, Long playerId, String name, double points, int gamesPlayed, int elo,
                            double adjustment, String adjustmentNote, int byes, boolean disqualified) {}
