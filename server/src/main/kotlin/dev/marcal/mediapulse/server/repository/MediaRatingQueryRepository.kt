package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.ratings.MediaRatingDto
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.repository.crud.MediaRatingRepository
import org.springframework.stereotype.Repository

@Repository
class MediaRatingQueryRepository(
    private val mediaRatingRepository: MediaRatingRepository,
) {
    fun findByEntity(
        entityType: EntityType,
        entityId: Long,
    ): MediaRatingDto? = mediaRatingRepository.findByEntityTypeAndEntityId(entityType, entityId)?.toDto()

    fun findByEntities(
        entityType: EntityType,
        entityIds: Collection<Long>,
    ): Map<Long, MediaRatingDto> {
        if (entityIds.isEmpty()) {
            return emptyMap()
        }
        return mediaRatingRepository
            .findByEntityTypeAndEntityIdIn(entityType, entityIds)
            .associate { rating -> rating.entityId to rating.toDto() }
    }

    private fun dev.marcal.mediapulse.server.model.rating.MediaRating.toDto(): MediaRatingDto =
        MediaRatingDto(
            rating = rating.toInt(),
            updatedAt = updatedAt,
        )
}
