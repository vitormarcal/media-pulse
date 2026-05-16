package dev.marcal.mediapulse.server.model.game

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "game_images")
data class GameImage(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "game_id", nullable = false)
    val gameId: Long,
    @Column(nullable = false)
    val url: String,
    @Column(nullable = false)
    val kind: String = "GRID",
    @Column(name = "is_primary", nullable = false)
    val isPrimary: Boolean = false,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
)
