package ch.ims.chess.chess_manager.controller;

import ch.ims.chess.chess_manager.dto.PointAdjustmentRequest;
import ch.ims.chess.chess_manager.dto.StandingEntry;
import ch.ims.chess.chess_manager.dto.TournamentCreateRequest;
import ch.ims.chess.chess_manager.dto.TournamentResponse;
import ch.ims.chess.chess_manager.service.TournamentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private final TournamentService tournamentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TournamentResponse create(@Valid @RequestBody TournamentCreateRequest req) {
        return tournamentService.create(req);
    }

    @GetMapping
    public List<TournamentResponse> list() {
        return tournamentService.list();
    }

    @GetMapping("/{id}")
    public TournamentResponse get(@PathVariable Long id) {
        return tournamentService.get(id);
    }

    @PostMapping("/{id}/players/{playerId}")
    public TournamentResponse enroll(@PathVariable Long id, @PathVariable Long playerId) {
        return tournamentService.enroll(id, playerId);
    }

    @DeleteMapping("/{id}/players/{playerId}")
    public TournamentResponse unenroll(@PathVariable Long id, @PathVariable Long playerId) {
        return tournamentService.unenroll(id, playerId);
    }

    @PostMapping("/{id}/players/{playerId}/points")
    public TournamentResponse adjustPoints(@PathVariable Long id, @PathVariable Long playerId,
                                           @Valid @RequestBody PointAdjustmentRequest req) {
        return tournamentService.adjustPoints(id, playerId, req);
    }

    @PostMapping("/{id}/players/{playerId}/disqualify")
    public TournamentResponse disqualify(@PathVariable Long id, @PathVariable Long playerId) {
        return tournamentService.disqualify(id, playerId);
    }

    @DeleteMapping("/{id}/players/{playerId}/disqualify")
    public TournamentResponse reinstate(@PathVariable Long id, @PathVariable Long playerId) {
        return tournamentService.reinstate(id, playerId);
    }

    @PatchMapping("/{id}/archive")
    public TournamentResponse archive(@PathVariable Long id) {
        return tournamentService.archive(id, true);
    }

    @PatchMapping("/{id}/unarchive")
    public TournamentResponse unarchive(@PathVariable Long id) {
        return tournamentService.archive(id, false);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        tournamentService.delete(id);
    }

    @PostMapping("/{id}/generate-round-robin")
    public TournamentResponse generateRoundRobin(@PathVariable Long id) {
        return tournamentService.generateRoundRobin(id);
    }

    /** Swiss system: pairs the next round based on current standings. */
    @PostMapping("/{id}/generate-next-round")
    public TournamentResponse generateNextRound(@PathVariable Long id) {
        return tournamentService.generateNextSwissRound(id);
    }

    @GetMapping("/{id}/standings")
    public List<StandingEntry> standings(@PathVariable Long id) {
        return tournamentService.standings(id);
    }
}
