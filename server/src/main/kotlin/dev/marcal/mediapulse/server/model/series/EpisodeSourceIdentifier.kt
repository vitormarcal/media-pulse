package dev.marcal.mediapulse.server.model.series

import dev.marcal.mediapulse.server.model.SourceIdentifier
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
@Table(name = "episode_source_identifiers")
data class EpisodeSourceIdentifier(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Enumerated(EnumType.STRING)
    val externalType: SourceIdentifier,
    val externalId: String,
    @Column(name = "episode_source_id", nullable = false)
    val episodeSourceId: Long = 0,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
)
