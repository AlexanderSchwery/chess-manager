package ch.ims.chess.chess_manager.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Table name avoids the SQL keyword MATCH. */
@Entity
@Table(name = "chess_match")
@Getter
@Setter
@NoArgsConstructor
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Tournament tournament;

    @Column(nullable = false)
    private Integer roundNumber;

    @ManyToOne(optional = false)
    private Player whitePlayer;

    /** null for a bye (spielfrei). */
    @ManyToOne
    private Player blackPlayer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchResult result = MatchResult.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status = MatchStatus.SCHEDULED;

    private Integer whiteEloBefore;
    private Integer blackEloBefore;
    private Integer whiteEloChange;
    private Integer blackEloChange;

    public boolean isBye() { return result == MatchResult.BYE; }
}
