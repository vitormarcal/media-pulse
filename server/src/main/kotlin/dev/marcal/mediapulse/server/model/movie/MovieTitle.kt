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
@Table(name = "movie_titles")
data class MovieTitle(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "movie_id", nullable = false)
    val movieId: Long,
    @Column(nullable = false)
    val title: String,
    @Column(nullable = true)
    val locale: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val source: MovieTitleSource,
    @Column(name = "is_primary", nullable = false)
    val isPrimary: Boolean = false,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
)
