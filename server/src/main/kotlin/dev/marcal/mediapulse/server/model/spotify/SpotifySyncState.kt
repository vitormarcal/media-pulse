package dev.marcal.mediapulse.server.model.spotify

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "spotify_sync_state")
data class SpotifySyncState(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "cursor_after_ms", nullable = false)
    val cursorAfterMs: Long = 0,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
)
