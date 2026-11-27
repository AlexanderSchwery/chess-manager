package ch.ims.chess.chess_manager.service;

import ch.ims.chess.chess_manager.exception.BusinessRuleException;
import ch.ims.chess.chess_manager.model.Player;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PairingService {

    public record Pairing(Player white, Player black) {
        Pairing swapped() { return new Pairing(black, white); }
    }

    public static String pairKey(Long a, Long b) {
        return Math.min(a, b) + "-" + Math.max(a, b);
    }

    /**
     * Double round robin via circle method. Returns one list of pairings per round
     * (first leg, then second leg with colours swapped). With an odd number of
     * players, the player facing the dummy gets a bye (no match created).
     */
    public List<List<Pairing>> doubleRoundRobin(List<Player> players) {
        List<Player> list = new ArrayList<>(players);
        if (list.size() % 2 == 1) list.add(null);
        int m = list.size();
        int rounds = m - 1;

        List<List<Pairing>> firstLeg = new ArrayList<>();
        for (int r = 0; r < rounds; r++) {
            List<Pairing> round = new ArrayList<>();
            for (int i = 0; i < m / 2; i++) {
                Player a = list.get(i);
                Player b = list.get(m - 1 - i);
                if (a == null || b == null) continue;
                boolean aWhite = (i == 0) ? (r % 2 == 0) : (i % 2 == 0);
                round.add(aWhite ? new Pairing(a, b) : new Pairing(b, a));
            }
            firstLeg.add(round);
            list.add(1, list.remove(m - 1)); // rotate everything except the first seat
        }

        List<List<Pairing>> all = new ArrayList<>(firstLeg);
        for (List<Pairing> round : firstLeg) {
            all.add(round.stream().map(Pairing::swapped).toList());
        }
        return all;
    }

    /**
     * Swiss pairing: sort by points then Elo, pair top-down without repeating
     * earlier pairings (backtracking). With an odd count the lowest ranked
     * player without a previous bye sits out.
     */
    public List<Pairing> swiss(List<Player> players, Map<Long, Double> points, Set<String> playedPairs,
                               Map<Long, Integer> whiteCounts, Set<Long> previousByes) {
        return swissRound(players, points, playedPairs, whiteCounts, previousByes).pairings();
    }

    /** Result of one Swiss round: the pairings plus the player with a bye (null if the count is even). */
    public record SwissRound(List<Pairing> pairings, Player bye) {}

    /**
     * FIDE rule (Basic Rules for Swiss Systems, art. 3/4): with an odd count one player gets a
     * pairing-allocated bye worth a win. Preferably a player who has not had a bye yet, taken from the
     * lowest score group and there the lowest rated, as long as everybody else can still be paired.
     */
    public SwissRound swissRound(List<Player> players, Map<Long, Double> points, Set<String> playedPairs,
                                 Map<Long, Integer> whiteCounts, Set<Long> previousByes) {
        List<Player> sorted = new ArrayList<>(players);
        sorted.sort(Comparator
                .comparing((Player p) -> points.getOrDefault(p.getId(), 0.0)).reversed()
                .thenComparing(Comparator.comparing(Player::getCurrentElo).reversed())
                .thenComparing(Player::getId));

        List<Pairing> result = null;
        Player byePlayer = null;
        if (sorted.size() % 2 == 0) {
            result = match(sorted, playedPairs, whiteCounts);
        } else {
            List<Player> byeOrder = new ArrayList<>(sorted);
            Collections.reverse(byeOrder);
            // players without a previous bye first (stable sort keeps lowest ranked first)
            byeOrder.sort(Comparator.comparing(p -> previousByes.contains(p.getId())));
            for (Player bye : byeOrder) {
                List<Player> rest = new ArrayList<>(sorted);
                rest.remove(bye);
                result = match(rest, playedPairs, whiteCounts);
                if (result != null) { byePlayer = bye; break; }
            }
        }
        if (result == null) {
            throw new BusinessRuleException("No valid Swiss pairing without repeated matches is possible");
        }
        return new SwissRound(result, byePlayer);
    }

    private List<Pairing> match(List<Player> remaining, Set<String> played, Map<Long, Integer> whiteCounts) {
        if (remaining.isEmpty()) return new ArrayList<>();
        Player first = remaining.get(0);
        for (int i = 1; i < remaining.size(); i++) {
            Player second = remaining.get(i);
            if (played.contains(pairKey(first.getId(), second.getId()))) continue;
            List<Player> rest = new ArrayList<>(remaining);
            rest.remove(i);
            rest.remove(0);
            List<Pairing> sub = match(rest, played, whiteCounts);
            if (sub != null) {
                sub.add(0, colour(first, second, whiteCounts));
                return sub;
            }
        }
        return null;
    }

    /** The player with fewer white games so far gets white; ties favour the higher ranked player. */
    private Pairing colour(Player higher, Player lower, Map<Long, Integer> whiteCounts) {
        int wh = whiteCounts.getOrDefault(higher.getId(), 0);
        int wl = whiteCounts.getOrDefault(lower.getId(), 0);
        return wl < wh ? new Pairing(lower, higher) : new Pairing(higher, lower);
    }
}
