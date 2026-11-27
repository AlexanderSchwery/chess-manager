package ch.ims.chess.chess_manager.model;

public enum MatchResult {
    WHITE_WINS(1.0), BLACK_WINS(0.0), DRAW(0.5), PENDING(null),
    /** Pairing-allocated bye (spielfrei): counts as a win, no opponent, no Elo change. */
    BYE(1.0);

    private final Double whiteScore;

    MatchResult(Double whiteScore) { this.whiteScore = whiteScore; }

    /** Score of the white player, null while PENDING. */
    public Double getWhiteScore() { return whiteScore; }

    /** Score of the black player, null while PENDING. */
    public Double getBlackScore() { return whiteScore == null ? null : 1.0 - whiteScore; }
}
