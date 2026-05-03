package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.comment.MediaComment
import org.springframework.data.jpa.repository.JpaRepository

interface MediaCommentRepository : JpaRepository<MediaComment, Long> {
    fun findByEntityTypeAndEntityIdOrderByCommentedAtDescIdDesc(
        entityType: EntityType,
        entityId: Long,
    ): List<MediaComment>
}
