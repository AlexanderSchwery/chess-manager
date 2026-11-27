package ch.ims.chess.chess_manager.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Manual bonus / penalty points for a player within one tournament. */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class PointAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Tournament tournament;

    @ManyToOne(optional = false)
    private Player player;

    @Column(nullable = false)
    private double points;

    private String reason;

    private LocalDateTime createdAt = LocalDateTime.now();
}
