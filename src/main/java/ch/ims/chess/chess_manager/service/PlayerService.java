package ch.ims.chess.chess_manager.service;

import ch.ims.chess.chess_manager.dto.PlayerCreateRequest;
import ch.ims.chess.chess_manager.dto.PlayerResponse;
import ch.ims.chess.chess_manager.exception.ResourceNotFoundException;
import ch.ims.chess.chess_manager.model.Player;
import ch.ims.chess.chess_manager.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerResponse create(PlayerCreateRequest req) {
        Player p = new Player();
        p.setName(req.name().trim());
        p.setCurrentElo(req.currentElo());
        String ssb = req.ssbNumber();
        p.setSsbNumber(ssb == null || ssb.isBlank() ? null : ssb.trim());
        return PlayerResponse.from(playerRepository.save(p));
    }

    @Transactional(readOnly = true)
    public List<PlayerResponse> list(String search) {
        List<Player> players;
        if (search == null || search.isBlank()) {
            players = playerRepository.findAll(Sort.by("name"));
        } else {
            String s = search.trim();
            players = playerRepository.findByNameContainingIgnoreCaseOrSsbNumberContainingIgnoreCase(s, s);
        }
        return players.stream().map(PlayerResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PlayerResponse get(Long id) {
        return PlayerResponse.from(find(id));
    }

    public PlayerResponse updateElo(Long id, int elo) {
        Player p = find(id);
        p.setCurrentElo(elo);
        return PlayerResponse.from(p);
    }

    public Player find(Long id) {
        return playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Player " + id + " not found"));
    }
}
