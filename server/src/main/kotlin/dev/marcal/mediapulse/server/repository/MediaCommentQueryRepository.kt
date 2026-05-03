package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.comments.MediaCommentDto
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.repository.crud.MediaCommentRepository
import org.springframework.stereotype.Repository

@Repository
class MediaCommentQueryRepository(
    private val mediaCommentRepository: MediaCommentRepository,
) {
    fun findByEntity(
        entityType: EntityType,
        entityId: Long,
    ): List<MediaCommentDto> =
        mediaCommentRepository
            .findByEntityTypeAndEntityIdOrderByCommentedAtDescIdDesc(entityType, entityId)
            .map { comment ->
                MediaCommentDto(
                    id = comment.id,
                    body = comment.body,
                    commentedAt = comment.commentedAt,
                    createdAt = comment.createdAt,
                    updatedAt = comment.updatedAt,
                    edited = comment.updatedAt.isAfter(comment.createdAt),
                )
            }
}
