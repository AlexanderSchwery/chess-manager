package ch.ims.chess.chess_manager.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Tournament {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TournamentType type;

    private Integer totalRounds;

    @Column(nullable = false)
    private Integer currentRound = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TournamentStatus status = TournamentStatus.CREATED;

    @ManyToMany
    @JoinTable(name = "tournament_participants",
            joinColumns = @JoinColumn(name = "tournament_id"),
            inverseJoinColumns = @JoinColumn(name = "player_id"))
    private Set<Player> participants = new LinkedHashSet<>();

    /** Players disqualified from this tournament (kept as participants for history). */
    @ElementCollection
    @CollectionTable(name = "tournament_disqualified", joinColumns = @JoinColumn(name = "tournament_id"))
    @Column(name = "player_id")
    private Set<Long> disqualified = new LinkedHashSet<>();

    @Column(nullable = false)
    private boolean archived = false;
}
