package dev.marcal.mediapulse.server.model.rating

import dev.marcal.mediapulse.server.model.EntityType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "media_ratings",
    uniqueConstraints = [UniqueConstraint(columnNames = ["entity_type", "entity_id"])],
)
data class MediaRating(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    val entityType: EntityType,
    @Column(name = "entity_id", nullable = false)
    val entityId: Long,
    @Column(nullable = false, columnDefinition = "SMALLINT")
    val rating: Short,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
)
