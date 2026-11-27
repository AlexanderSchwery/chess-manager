package ch.ims.chess.chess_manager.service;

import ch.ims.chess.chess_manager.dto.PointAdjustmentRequest;
import ch.ims.chess.chess_manager.dto.StandingEntry;
import ch.ims.chess.chess_manager.dto.TournamentCreateRequest;
import ch.ims.chess.chess_manager.dto.TournamentResponse;
import ch.ims.chess.chess_manager.exception.BusinessRuleException;
import ch.ims.chess.chess_manager.exception.ResourceNotFoundException;
import ch.ims.chess.chess_manager.model.*;
import ch.ims.chess.chess_manager.repository.MatchRepository;
import ch.ims.chess.chess_manager.repository.PointAdjustmentRepository;
import ch.ims.chess.chess_manager.repository.TournamentRepository;
import ch.ims.chess.chess_manager.service.PairingService.Pairing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Transactional
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final MatchRepository matchRepository;
    private final PointAdjustmentRepository adjustmentRepository;
    private final PlayerService playerService;
    private final PairingService pairingService;

    public TournamentResponse create(TournamentCreateRequest req) {
        Tournament t = new Tournament();
        t.setName(req.name().trim());
        t.setType(req.type());
        t.setTotalRounds(req.totalRounds());
        return TournamentResponse.from(tournamentRepository.save(t));
    }

    @Transactional(readOnly = true)
    public List<TournamentResponse> list() {
        return tournamentRepository.findAll().stream().map(TournamentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TournamentResponse get(Long id) {
        return TournamentResponse.from(find(id));
    }

    public TournamentResponse enroll(Long tournamentId, Long playerId) {
        Tournament t = find(tournamentId);
        requireActive(t);
        Player p = playerService.find(playerId);
        if (t.getStatus() == TournamentStatus.COMPLETED) {
            throw new BusinessRuleException("Tournament is already completed");
        }
        if (t.getParticipants().contains(p)) {
            throw new BusinessRuleException("Player is already enrolled");
        }
        resetRoundRobinIfUntouched(t);
        t.getParticipants().add(p);
        return TournamentResponse.from(t);
    }

    /** Removes a player. Not possible once the player has played games (use disqualify instead). */
    public TournamentResponse unenroll(Long tournamentId, Long playerId) {
        Tournament t = find(tournamentId);
        requireActive(t);
        Player p = playerService.find(playerId);
        if (!t.getParticipants().contains(p)) {
            throw new BusinessRuleException("Player is not enrolled");
        }
        List<Match> own = matchRepository.findByTournamentId(tournamentId).stream()
                .filter(m -> involves(m, playerId)).toList();
        if (own.stream().anyMatch(m -> m.getStatus() == MatchStatus.COMPLETED)) {
            throw new BusinessRuleException(
                    "Player already has played games in this tournament - disqualify the player instead");
        }
        if (t.getType() == TournamentType.DOUBLE_ROUND_ROBIN) {
            resetRoundRobinIfUntouched(t);
        } else {
            matchRepository.deleteAll(own); // only scheduled / cancelled matches remain
        }
        t.getParticipants().remove(p);
        t.getDisqualified().remove(playerId);
        adjustmentRepository.deleteAll(adjustmentRepository.findByTournamentId(tournamentId).stream()
                .filter(a -> a.getPlayer().getId().equals(playerId)).toList());
        return TournamentResponse.from(t);
    }

    /**
     * A round robin schedule depends on the exact player set. If no game was played yet the schedule is
     * dropped so it can be generated again; otherwise the participants are fixed.
     */
    private void resetRoundRobinIfUntouched(Tournament t) {
        if (t.getType() != TournamentType.DOUBLE_ROUND_ROBIN) return;
        List<Match> all = matchRepository.findByTournamentId(t.getId());
        if (all.isEmpty()) return;
        if (all.stream().anyMatch(m -> m.getStatus() == MatchStatus.COMPLETED)) {
            throw new BusinessRuleException(
                    "Round robin games were already played; participants are fixed (disqualify instead)");
        }
        matchRepository.deleteAll(all);
        t.setTotalRounds(null);
        t.setCurrentRound(1);
        t.setStatus(TournamentStatus.CREATED);
    }

    private static boolean involves(Match m, Long playerId) {
        return m.getWhitePlayer().getId().equals(playerId)
                || (m.getBlackPlayer() != null && m.getBlackPlayer().getId().equals(playerId));
    }

    /** Adds bonus (positive) or penalty (negative) points for a player in this tournament. */
    public TournamentResponse adjustPoints(Long tournamentId, Long playerId, PointAdjustmentRequest req) {
        Tournament t = find(tournamentId);
        requireActive(t);
        Player p = playerService.find(playerId);
        if (!t.getParticipants().contains(p)) {
            throw new BusinessRuleException("Player is not enrolled in this tournament");
        }
        if (req.points() == 0 || !Double.isFinite(req.points())) {
            throw new BusinessRuleException("Points must be a non-zero number");
        }
        PointAdjustment a = new PointAdjustment();
        a.setTournament(t);
        a.setPlayer(p);
        a.setPoints(req.points());
        a.setReason(req.reason() == null || req.reason().isBlank() ? null : req.reason().trim());
        adjustmentRepository.save(a);
        return TournamentResponse.from(t);
    }

    /**
     * Disqualification: the player is excluded from further pairings, open games are forfeited
     * (opponent gets the win, no Elo change) and the player is ranked last.
     */
    public TournamentResponse disqualify(Long tournamentId, Long playerId) {
        Tournament t = find(tournamentId);
        requireActive(t);
        Player p = playerService.find(playerId);
        if (!t.getParticipants().contains(p)) {
            throw new BusinessRuleException("Player is not enrolled in this tournament");
        }
        if (!t.getDisqualified().add(playerId)) {
            throw new BusinessRuleException("Player is already disqualified");
        }
        for (Match m : matchRepository.findByTournamentId(tournamentId)) {
            if (m.getStatus() != MatchStatus.SCHEDULED || !involves(m, playerId)) continue;
            boolean playerIsWhite = m.getWhitePlayer().getId().equals(playerId);
            m.setResult(playerIsWhite ? MatchResult.BLACK_WINS : MatchResult.WHITE_WINS);
            m.setStatus(MatchStatus.COMPLETED);
            m.setWhiteEloBefore(m.getWhitePlayer().getCurrentElo());
            m.setBlackEloBefore(m.getBlackPlayer().getCurrentElo());
            m.setWhiteEloChange(0);
            m.setBlackEloChange(0);
        }
        refreshTournamentStatus(t);
        return TournamentResponse.from(t);
    }

    public TournamentResponse reinstate(Long tournamentId, Long playerId) {
        Tournament t = find(tournamentId);
        requireActive(t);
        if (!t.getDisqualified().remove(playerId)) {
            throw new BusinessRuleException("Player is not disqualified");
        }
        return TournamentResponse.from(t);
    }

    public TournamentResponse archive(Long id, boolean archived) {
        Tournament t = find(id);
        t.setArchived(archived);
        return TournamentResponse.from(t);
    }

    public void delete(Long id) {
        Tournament t = find(id);
        matchRepository.deleteAll(matchRepository.findByTournamentId(id));
        adjustmentRepository.deleteAll(adjustmentRepository.findByTournamentId(id));
        tournamentRepository.delete(t);
    }

    public void requireActive(Tournament t) {
        if (t.isArchived()) {
            throw new BusinessRuleException("Tournament is archived - restore it first");
        }
    }

    /** Fixed-length tournaments complete once the last round has no open matches. */
    public void refreshTournamentStatus(Tournament t) {
        if (t.getType() == TournamentType.FREE_PLAY || t.getTotalRounds() == null) return;
        List<Match> all = matchRepository.findByTournamentId(t.getId());
        boolean open = all.stream().anyMatch(x -> x.getStatus() == MatchStatus.SCHEDULED);
        int maxRound = all.stream().mapToInt(Match::getRoundNumber).max().orElse(0);
        if (!open && maxRound >= t.getTotalRounds()) {
            t.setStatus(TournamentStatus.COMPLETED);
        }
    }

    public TournamentResponse generateRoundRobin(Long id) {
        Tournament t = find(id);
        requireActive(t);
        if (t.getType() != TournamentType.DOUBLE_ROUND_ROBIN) {
            throw new BusinessRuleException("Tournament is not a double round robin");
        }
        if (activePlayers(t).size() < 2) {
            throw new BusinessRuleException("At least 2 participants required");
        }
        if (!matchRepository.findByTournamentId(id).isEmpty()) {
            throw new BusinessRuleException("Pairings were already generated");
        }
        List<Player> players = new ArrayList<>(activePlayers(t));
        players.sort(Comparator.comparing(Player::getId));
        List<List<Pairing>> rounds = pairingService.doubleRoundRobin(players);
        int roundNo = 1;
        for (List<Pairing> round : rounds) {
            for (Pairing p : round) {
                matchRepository.save(newMatch(t, roundNo, p.white(), p.black()));
            }
            roundNo++;
        }
        t.setTotalRounds(rounds.size());
        t.setCurrentRound(1);
        t.setStatus(TournamentStatus.IN_PROGRESS);
        return TournamentResponse.from(t);
    }

    public TournamentResponse generateNextSwissRound(Long id) {
        Tournament t = find(id);
        requireActive(t);
        if (t.getType() != TournamentType.SWISS) {
            throw new BusinessRuleException("Tournament is not a Swiss system tournament");
        }
        if (t.getStatus() == TournamentStatus.COMPLETED) {
            throw new BusinessRuleException("Tournament is already completed");
        }
        List<Player> active = activePlayers(t);
        int n = active.size();
        if (n < 2) throw new BusinessRuleException("At least 2 participants required");

        List<Match> matches = matchRepository.findByTournamentId(id);
        if (matches.stream().anyMatch(m -> m.getStatus() == MatchStatus.SCHEDULED)) {
            throw new BusinessRuleException("Finish all matches of the current round first");
        }
        int lastRound = matches.stream().mapToInt(Match::getRoundNumber).max().orElse(0);
        int nextRound = lastRound + 1;
        if (t.getTotalRounds() == null) {
            t.setTotalRounds(Math.max(1, (int) Math.ceil(Math.log(n) / Math.log(2))));
        }
        if (nextRound > t.getTotalRounds()) {
            throw new BusinessRuleException("All " + t.getTotalRounds() + " rounds are already generated");
        }

        Map<Long, Double> points = totalPoints(t, matches);
        Set<String> played = new HashSet<>();
        Map<Long, Integer> whiteCounts = new HashMap<>();
        Set<Long> byes = new HashSet<>();
        for (Match m : matches) {
            if (m.getStatus() == MatchStatus.CANCELLED) continue;
            if (m.isBye()) {
                byes.add(m.getWhitePlayer().getId());
                continue;
            }
            Long w = m.getWhitePlayer().getId();
            played.add(PairingService.pairKey(w, m.getBlackPlayer().getId()));
            whiteCounts.merge(w, 1, Integer::sum);
        }

        PairingService.SwissRound round = pairingService.swissRound(active, points, played, whiteCounts, byes);
        for (Pairing p : round.pairings()) {
            matchRepository.save(newMatch(t, nextRound, p.white(), p.black()));
        }
        if (round.bye() != null) {
            Match bye = newMatch(t, nextRound, round.bye(), null);
            bye.setResult(MatchResult.BYE);
            bye.setStatus(MatchStatus.COMPLETED);
            matchRepository.save(bye);
        }
        t.setCurrentRound(nextRound);
        t.setStatus(TournamentStatus.IN_PROGRESS);
        refreshTournamentStatus(t);
        return TournamentResponse.from(t);
    }

    private List<Player> activePlayers(Tournament t) {
        return t.getParticipants().stream().filter(p -> !t.getDisqualified().contains(p.getId())).toList();
    }

    @Transactional(readOnly = true)
    public List<StandingEntry> standings(Long id) {
        Tournament t = find(id);
        List<Match> matches = matchRepository.findByTournamentId(id);
        Map<Long, Double> matchPoints = computePoints(matches);
        Map<Long, Double> adjustments = new HashMap<>();
        Map<Long, List<String>> notes = new HashMap<>();
        for (PointAdjustment a : adjustmentRepository.findByTournamentId(id)) {
            Long pid = a.getPlayer().getId();
            adjustments.merge(pid, a.getPoints(), Double::sum);
            notes.computeIfAbsent(pid, k -> new ArrayList<>()).add(
                    (a.getPoints() > 0 ? "+" : "") + a.getPoints() + (a.getReason() == null ? "" : " (" + a.getReason() + ")"));
        }
        Map<Long, Integer> games = new HashMap<>();
        Map<Long, Integer> byes = new HashMap<>();
        Map<Long, List<GameScore>> played = new HashMap<>();
        for (Match m : matches) {
            if (m.getStatus() != MatchStatus.COMPLETED) continue;
            Long w = m.getWhitePlayer().getId();
            if (m.isBye()) {
                byes.merge(w, 1, Integer::sum);
                continue;
            }
            Long b = m.getBlackPlayer().getId();
            games.merge(w, 1, Integer::sum);
            games.merge(b, 1, Integer::sum);
            played.computeIfAbsent(w, k -> new ArrayList<>()).add(new GameScore(b, m.getResult().getWhiteScore()));
            played.computeIfAbsent(b, k -> new ArrayList<>()).add(new GameScore(w, m.getResult().getBlackScore()));
        }
        Function<Long, Double> totalById = pid ->
                matchPoints.getOrDefault(pid, 0.0) + adjustments.getOrDefault(pid, 0.0);
        Function<Player, Double> total = p -> totalById.apply(p.getId());
        // Buchholz: sum of the scores of all opponents actually played (byes count nothing)
        Function<Player, Double> buchholz = p -> played.getOrDefault(p.getId(), List.of()).stream()
                .mapToDouble(g -> totalById.apply(g.opponentId())).sum();
        // Sonneborn-Berger: full score of every opponent beaten plus half the score of every opponent drawn
        Function<Player, Double> sonnebornBerger = p -> played.getOrDefault(p.getId(), List.of()).stream()
                .mapToDouble(g -> g.score() * totalById.apply(g.opponentId())).sum();

        // In a round robin everybody meets the same opponents, so Buchholz cannot separate tied players;
        // there Sonneborn-Berger is the first tie-break (FIDE practice), in Swiss it follows Buchholz.
        Comparator<Player> order = Comparator
                .comparing((Player p) -> t.getDisqualified().contains(p.getId()))
                .thenComparing(Comparator.comparing(total).reversed());
        if (t.getType() == TournamentType.DOUBLE_ROUND_ROBIN) {
            order = order.thenComparing(Comparator.comparing(sonnebornBerger).reversed());
        } else {
            order = order.thenComparing(Comparator.comparing(buchholz).reversed())
                    .thenComparing(Comparator.comparing(sonnebornBerger).reversed());
        }
        order = order.thenComparing(Comparator.comparing(Player::getCurrentElo).reversed())
                .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER);
        List<Player> players = new ArrayList<>(t.getParticipants());
        players.sort(order);
        List<StandingEntry> result = new ArrayList<>();
        int rank = 1;
        for (Player p : players) {
            result.add(new StandingEntry(rank++, p.getId(), p.getName(), total.apply(p),
                    games.getOrDefault(p.getId(), 0), p.getCurrentElo(),
                    adjustments.getOrDefault(p.getId(), 0.0),
                    notes.containsKey(p.getId()) ? String.join(", ", notes.get(p.getId())) : null,
                    byes.getOrDefault(p.getId(), 0), t.getDisqualified().contains(p.getId()),
                    buchholz.apply(p), sonnebornBerger.apply(p)));
        }
        return result;
    }

    /** One played game from a player's point of view: the opponent and the score the player made (1, 0.5, 0). */
    private record GameScore(Long opponentId, double score) {}

    private Map<Long, Double> totalPoints(Tournament t, List<Match> matches) {
        Map<Long, Double> points = computePoints(matches);
        for (PointAdjustment a : adjustmentRepository.findByTournamentId(t.getId())) {
            points.merge(a.getPlayer().getId(), a.getPoints(), Double::sum);
        }
        return points;
    }

    /** Points per player from COMPLETED matches only: win 1 (bye counts as a win), draw 0.5, loss 0. */
    private Map<Long, Double> computePoints(List<Match> matches) {
        Map<Long, Double> points = new HashMap<>();
        for (Match m : matches) {
            if (m.getStatus() != MatchStatus.COMPLETED || m.getResult().getWhiteScore() == null) continue;
            points.merge(m.getWhitePlayer().getId(), m.getResult().getWhiteScore(), Double::sum);
            if (m.getBlackPlayer() != null) {
                points.merge(m.getBlackPlayer().getId(), m.getResult().getBlackScore(), Double::sum);
            }
        }
        return points;
    }

    private Match newMatch(Tournament t, int round, Player white, Player black) {
        Match m = new Match();
        m.setTournament(t);
        m.setRoundNumber(round);
        m.setWhitePlayer(white);
        m.setBlackPlayer(black);
        return m;
    }

    public Tournament find(Long id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament " + id + " not found"));
    }
}
