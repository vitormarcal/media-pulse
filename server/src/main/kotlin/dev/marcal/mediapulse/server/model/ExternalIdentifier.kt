package dev.marcal.mediapulse.server.model

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
    name = "external_identifiers",
    uniqueConstraints = [UniqueConstraint(columnNames = ["provider", "external_id"])],
)
data class ExternalIdentifier(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    val entityType: EntityType,
    @Column(name = "entity_id", nullable = false)
    val entityId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val provider: Provider,
    @Column(name = "external_id", nullable = false)
    val externalId: String,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
)
