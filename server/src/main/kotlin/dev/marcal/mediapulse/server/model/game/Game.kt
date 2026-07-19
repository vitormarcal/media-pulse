package dev.marcal.mediapulse.server.model.game

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "games")
data class Game(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val title: String,
    @Column(name = "original_title", nullable = false)
    val originalTitle: String,
    val year: Int? = null,
    @Column(columnDefinition = "TEXT")
    val description: String? = null,
    @Column(name = "cover_url")
    val coverUrl: String? = null,
    @Column(name = "igdb_id", unique = true)
    val igdbId: String? = null,
    @Column(name = "steamgriddb_id", unique = true)
    val steamGridDbId: String? = null,
    val slug: String? = null,
    @Column(nullable = false, unique = true)
    val fingerprint: String,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at")
    val updatedAt: Instant? = null,
)
