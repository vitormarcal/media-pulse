package dev.marcal.mediapulse.server.model.tv

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
@Table(name = "tv_episode_watches")
data class TvEpisodeWatch(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "episode_id", nullable = false)
    val episodeId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    val source: TvEpisodeWatchSource,
    @Column(name = "watched_at", nullable = false)
    val watchedAt: Instant,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
)
