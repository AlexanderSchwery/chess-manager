package ch.ims.chess.chess_manager.dto;

import ch.ims.chess.chess_manager.model.*;

public record MatchResponse(Long id, Long tournamentId, Integer roundNumber,
                            Long whitePlayerId, String whitePlayerName,
                            Long blackPlayerId, String blackPlayerName,
                            MatchResult result, MatchStatus status,
                            Integer whiteEloBefore, Integer blackEloBefore,
                            Integer whiteEloChange, Integer blackEloChange,
                            boolean bye) {
    public static MatchResponse from(Match m) {
        Player b = m.getBlackPlayer();
        return new MatchResponse(m.getId(), m.getTournament().getId(), m.getRoundNumber(),
                m.getWhitePlayer().getId(), m.getWhitePlayer().getName(),
                b == null ? null : b.getId(), b == null ? null : b.getName(),
                m.getResult(), m.getStatus(),
                m.getWhiteEloBefore(), m.getBlackEloBefore(),
                m.getWhiteEloChange(), m.getBlackEloChange(), m.isBye());
    }
}
