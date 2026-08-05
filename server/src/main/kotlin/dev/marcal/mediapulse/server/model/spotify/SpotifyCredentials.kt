package dev.marcal.mediapulse.server.model.spotify

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "spotify_credentials")
data class SpotifyCredentials(
    @Id
    val id: Long = 1,
    @Column(name = "refresh_token", nullable = false)
    val refreshToken: String,
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
)
