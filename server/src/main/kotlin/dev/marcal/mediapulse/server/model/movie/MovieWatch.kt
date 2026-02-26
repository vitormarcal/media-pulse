package dev.marcal.mediapulse.server.model.movie

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
@Table(name = "movie_watches")
data class MovieWatch(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "movie_id", nullable = false)
    val movieId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    val source: MovieWatchSource,
    @Column(name = "watched_at", nullable = false)
    val watchedAt: Instant,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
)
