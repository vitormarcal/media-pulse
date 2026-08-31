package dev.marcal.mediapulse.server.model.person

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "people")
data class Person(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "tmdb_id", nullable = false, unique = true)
    val tmdbId: String,
    @Column(nullable = false)
    val name: String,
    @Column(name = "normalized_name", nullable = false)
    val normalizedName: String,
    @Column(nullable = false, unique = true)
    val slug: String,
    @Column(name = "profile_url")
    val profileUrl: String? = null,
    @Column(columnDefinition = "TEXT")
    val biography: String? = null,
    val birthday: String? = null,
    val deathday: String? = null,
    @Column(name = "place_of_birth")
    val placeOfBirth: String? = null,
    @Column(name = "known_for_department")
    val knownForDepartment: String? = null,
    val homepage: String? = null,
    @Column(name = "imdb_id")
    val imdbId: String? = null,
    val popularity: Double? = null,
    @Column(name = "tmdb_synced_at")
    val tmdbSyncedAt: Instant? = null,
    @Column(name = "tmdb_sync_attempted_at")
    val tmdbSyncAttemptedAt: Instant? = null,
    @Column(name = "tmdb_sync_error")
    val tmdbSyncError: String? = null,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at")
    val updatedAt: Instant? = null,
)
