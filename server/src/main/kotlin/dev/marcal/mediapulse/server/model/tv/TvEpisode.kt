package dev.marcal.mediapulse.server.model.tv

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "tv_episodes")
data class TvEpisode(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "show_id", nullable = false)
    val showId: Long,
    @Column(nullable = false)
    val title: String,
    @Column(name = "season_number")
    val seasonNumber: Int? = null,
    @Column(name = "episode_number")
    val episodeNumber: Int? = null,
    @Column(columnDefinition = "TEXT")
    val summary: String? = null,
    @Column(name = "duration_ms")
    val durationMs: Int? = null,
    @Column(name = "originally_available_at")
    val originallyAvailableAt: LocalDate? = null,
    @Column(nullable = false, unique = true)
    val fingerprint: String,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at")
    val updatedAt: Instant? = null,
)
