package ch.ims.chess.chess_manager.service;

import ch.ims.chess.chess_manager.model.Match;
import ch.ims.chess.chess_manager.model.MatchResult;
import ch.ims.chess.chess_manager.model.Player;
import org.springframework.stereotype.Service;

@Service
public class EloService {

    public static final int K_FACTOR = 20;
    private static final double MAX_DIFF = 400.0;

    public record EloChange(int whiteChange, int blackChange) {}

    /** Expected score of a player rated {@code own} against {@code opponent}; rating difference capped at 400. */
    public double expectedScore(int own, int opponent) {
        double diff = Math.max(-MAX_DIFF, Math.min(MAX_DIFF, opponent - own));
        return 1.0 / (1.0 + Math.pow(10.0, diff / 400.0));
    }

    public EloChange calculate(int whiteElo, int blackElo, MatchResult result) {
        Double sw = result.getWhiteScore();
        Double sb = result.getBlackScore();
        if (sw == null || sb == null) {
            throw new IllegalArgumentException("Cannot calculate Elo for result " + result);
        }
        double ew = expectedScore(whiteElo, blackElo);
        double eb = 1.0 - ew;
        int white = (int) Math.round(K_FACTOR * (sw - ew));
        int black = (int) Math.round(K_FACTOR * (sb - eb));
        return new EloChange(white, black);
    }

    /** Stores Elo history on the match and updates both players' current Elo. */
    public void applyResult(Match match, MatchResult result) {
        Player white = match.getWhitePlayer();
        Player black = match.getBlackPlayer();
        int wBefore = white.getCurrentElo();
        int bBefore = black.getCurrentElo();
        EloChange change = calculate(wBefore, bBefore, result);

        match.setWhiteEloBefore(wBefore);
        match.setBlackEloBefore(bBefore);
        match.setWhiteEloChange(change.whiteChange());
        match.setBlackEloChange(change.blackChange());
        white.setCurrentElo(wBefore + change.whiteChange());
        black.setCurrentElo(bBefore + change.blackChange());
    }
}
