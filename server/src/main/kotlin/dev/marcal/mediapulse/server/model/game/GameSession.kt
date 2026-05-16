package dev.marcal.mediapulse.server.model.game

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "game_sessions")
data class GameSession(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "game_id", nullable = false)
    val gameId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: GameSessionStatus,
    @Column(name = "started_at", nullable = false)
    val startedAt: Instant,
    @Column(name = "ended_at")
    val endedAt: Instant? = null,
    @Column(columnDefinition = "TEXT")
    val notes: String? = null,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at")
    val updatedAt: Instant? = null,
)
