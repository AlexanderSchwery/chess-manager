package ch.ims.chess.chess_manager.controller;

import ch.ims.chess.chess_manager.dto.MatchCreateRequest;
import ch.ims.chess.chess_manager.dto.MatchResponse;
import ch.ims.chess.chess_manager.dto.MatchResultRequest;
import ch.ims.chess.chess_manager.model.MatchStatus;
import ch.ims.chess.chess_manager.service.MatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @GetMapping("/tournaments/{id}/matches")
    public List<MatchResponse> list(@PathVariable Long id,
                                    @RequestParam(required = false) Integer round,
                                    @RequestParam(required = false) MatchStatus status) {
        return matchService.list(id, round, status);
    }

    @PostMapping("/tournaments/{id}/matches")
    @ResponseStatus(HttpStatus.CREATED)
    public MatchResponse create(@PathVariable Long id, @Valid @RequestBody MatchCreateRequest req) {
        return matchService.create(id, req);
    }

    @PostMapping("/matches/{id}/result")
    public MatchResponse recordResult(@PathVariable Long id, @Valid @RequestBody MatchResultRequest req) {
        return matchService.recordResult(id, req.result());
    }

    @PatchMapping("/matches/{id}/cancel")
    public MatchResponse cancel(@PathVariable Long id) {
        return matchService.cancel(id);
    }
}
