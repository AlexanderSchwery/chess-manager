package ch.ims.chess.chess_manager.controller;

import ch.ims.chess.chess_manager.dto.EloUpdateRequest;
import ch.ims.chess.chess_manager.dto.PlayerCreateRequest;
import ch.ims.chess.chess_manager.dto.PlayerHistory;
import ch.ims.chess.chess_manager.dto.PlayerResponse;
import ch.ims.chess.chess_manager.service.HistoryService;
import ch.ims.chess.chess_manager.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;
    private final HistoryService historyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlayerResponse create(@Valid @RequestBody PlayerCreateRequest req) {
        return playerService.create(req);
    }

    @GetMapping
    public List<PlayerResponse> list(@RequestParam(required = false) String search) {
        return playerService.list(search);
    }

    @GetMapping("/{id}")
    public PlayerResponse get(@PathVariable Long id) {
        return playerService.get(id);
    }

    @GetMapping("/{id}/history")
    public PlayerHistory history(@PathVariable Long id) {
        return historyService.history(id);
    }

    @PatchMapping("/{id}/elo")
    public PlayerResponse updateElo(@PathVariable Long id, @Valid @RequestBody EloUpdateRequest req) {
        return playerService.updateElo(id, req.currentElo());
    }
}
