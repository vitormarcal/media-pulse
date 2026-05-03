package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.rating.MediaRating
import org.springframework.data.jpa.repository.JpaRepository

interface MediaRatingRepository : JpaRepository<MediaRating, Long> {
    fun findByEntityTypeAndEntityId(
        entityType: EntityType,
        entityId: Long,
    ): MediaRating?

    fun findByEntityTypeAndEntityIdIn(
        entityType: EntityType,
        entityIds: Collection<Long>,
    ): List<MediaRating>

    fun deleteByEntityTypeAndEntityId(
        entityType: EntityType,
        entityId: Long,
    )
}
