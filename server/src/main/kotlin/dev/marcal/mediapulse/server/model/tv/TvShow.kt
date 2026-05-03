package dev.marcal.mediapulse.server.model.tv

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "tv_shows")
data class TvShow(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "original_title", nullable = false)
    val originalTitle: String,
    @Column(columnDefinition = "TEXT")
    val description: String? = null,
    @Column(name = "year")
    val year: Int? = null,
    @Column(name = "cover_url")
    val coverUrl: String? = null,
    @Column(name = "slug")
    val slug: String? = null,
    @Column(nullable = false, unique = true)
    val fingerprint: String,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at")
    val updatedAt: Instant? = null,
    @Column(name = "credits_synced_at")
    val creditsSyncedAt: Instant? = null,
)
