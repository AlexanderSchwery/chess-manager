package ch.ims.chess.chess_manager;

import ch.ims.chess.chess_manager.model.MatchResult;
import ch.ims.chess.chess_manager.model.Player;
import ch.ims.chess.chess_manager.service.EloService;
import ch.ims.chess.chess_manager.service.PairingService;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EloAndPairingTests {

    private final EloService elo = new EloService();
    private final PairingService pairing = new PairingService();

    private static Player player(long id, int elo) {
        Player p = new Player();
        p.setId(id);
        p.setName("P" + id);
        p.setCurrentElo(elo);
        return p;
    }

    @Test
    void equalRatingsWinGivesPlusMinusTen() {
        var c = elo.calculate(1500, 1500, MatchResult.WHITE_WINS);
        assertEquals(10, c.whiteChange());
        assertEquals(-10, c.blackChange());
    }

    @Test
    void drawBetweenEqualRatingsChangesNothing() {
        var c = elo.calculate(1500, 1500, MatchResult.DRAW);
        assertEquals(0, c.whiteChange());
        assertEquals(0, c.blackChange());
    }

    @Test
    void ratingDifferenceIsCappedAt400() {
        assertEquals(elo.calculate(2400, 1500, MatchResult.BLACK_WINS),
                elo.calculate(1900, 1500, MatchResult.BLACK_WINS));
    }

    @Test
    void pendingResultRejected() {
        assertThrows(IllegalArgumentException.class, () -> elo.calculate(1500, 1500, MatchResult.PENDING));
    }

    @Test
    void doubleRoundRobinEveryoneMeetsEveryoneTwiceWithBothColours() {
        for (int n : new int[]{2, 3, 4, 5, 6}) {
            List<Player> ps = new ArrayList<>();
            for (int i = 1; i <= n; i++) ps.add(player(i, 1500));
            var rounds = pairing.doubleRoundRobin(ps);
            int expectedRounds = 2 * (n % 2 == 0 ? n - 1 : n);
            assertEquals(expectedRounds, rounds.size(), "rounds for n=" + n);
            Set<String> directed = new HashSet<>();
            for (var round : rounds) {
                Set<Long> seen = new HashSet<>();
                for (var p : round) {
                    assertTrue(seen.add(p.white().getId()) && seen.add(p.black().getId()), "player twice in a round");
                    assertTrue(directed.add(p.white().getId() + ">" + p.black().getId()), "duplicate directed pairing");
                }
            }
            assertEquals(n * (n - 1), directed.size(), "games for n=" + n);
        }
    }

    @Test
    void swissAvoidsRepeatsAndGivesByeForOddCount() {
        List<Player> ps = List.of(player(1, 1600), player(2, 1500), player(3, 1400));
        var pairs = pairing.swiss(ps, Map.of(), Set.of(PairingService.pairKey(1L, 2L)), Map.of(), Set.of());
        assertEquals(1, pairs.size());
        Set<Long> ids = Set.of(pairs.get(0).white().getId(), pairs.get(0).black().getId());
        assertNotEquals(Set.of(1L, 2L), ids);
    }

    @Test
    void swissByeGoesToLowestRankedPlayerWithoutEarlierBye() {
        List<Player> ps = List.of(player(1, 1800), player(2, 1700), player(3, 1600), player(4, 1500), player(5, 1400));
        var round = pairing.swissRound(ps, Map.of(), Set.of(), Map.of(), Set.of());
        assertEquals(2, round.pairings().size());
        assertEquals(5L, round.bye().getId());

        var next = pairing.swissRound(ps, Map.of(), Set.of(), Map.of(), Set.of(5L));
        assertEquals(4L, next.bye().getId(), "player 5 already had a bye");
    }

    @Test
    void swissByeGoesToLowestScoreGroupBeforeLowestRating() {
        List<Player> ps = List.of(player(1, 1800), player(2, 1700), player(3, 1600), player(4, 1500), player(5, 1400));
        Map<Long, Double> pts = Map.of(1L, 1.0, 2L, 1.0, 3L, 1.0, 4L, 0.0, 5L, 1.0);
        assertEquals(4L, pairing.swissRound(ps, pts, Set.of(), Map.of(), Set.of()).bye().getId());
    }

    @Test
    void evenCountHasNoBye() {
        List<Player> ps = List.of(player(1, 1800), player(2, 1700));
        assertNull(pairing.swissRound(ps, Map.of(), Set.of(), Map.of(), Set.of()).bye());
    }
}
