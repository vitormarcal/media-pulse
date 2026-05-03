package dev.marcal.mediapulse.server.service.comment

import dev.marcal.mediapulse.server.api.comments.CreateMediaCommentRequest
import dev.marcal.mediapulse.server.api.comments.MediaCommentDto
import dev.marcal.mediapulse.server.api.comments.UpdateMediaCommentRequest
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.comment.MediaComment
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.repository.crud.BookRepository
import dev.marcal.mediapulse.server.repository.crud.MediaCommentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class MediaCommentService(
    private val mediaCommentRepository: MediaCommentRepository,
    private val movieRepository: MovieRepository,
    private val tvShowRepository: TvShowRepository,
    private val albumRepository: AlbumRepository,
    private val bookRepository: BookRepository,
) {
    @Transactional
    fun create(
        mediaType: String,
        entityId: Long,
        request: CreateMediaCommentRequest,
    ): MediaCommentDto {
        val entityType = resolveMediaType(mediaType)
        ensureEntityExists(entityType, entityId)
        val now = Instant.now()
        val saved =
            mediaCommentRepository.save(
                MediaComment(
                    entityType = entityType,
                    entityId = entityId,
                    body = normalizeBody(request.body),
                    commentedAt = request.commentedAt ?: now,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        return saved.toDto()
    }

    @Transactional
    fun update(
        commentId: Long,
        request: UpdateMediaCommentRequest,
    ): MediaCommentDto {
        val current =
            mediaCommentRepository.findById(commentId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "comentário não encontrado")
            }
        val updated =
            current.copy(
                body = normalizeBody(request.body),
                commentedAt = request.commentedAt ?: current.commentedAt,
                updatedAt = Instant.now(),
            )
        return mediaCommentRepository.save(updated).toDto()
    }

    private fun resolveMediaType(mediaType: String): EntityType =
        when (mediaType.trim().lowercase()) {
            "movies" -> EntityType.MOVIE
            "shows" -> EntityType.SHOW
            "albums" -> EntityType.ALBUM
            "books" -> EntityType.BOOK
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "mediaType inválido")
        }

    private fun ensureEntityExists(
        entityType: EntityType,
        entityId: Long,
    ) {
        val exists =
            when (entityType) {
                EntityType.MOVIE -> movieRepository.existsById(entityId)
                EntityType.SHOW -> tvShowRepository.existsById(entityId)
                EntityType.ALBUM -> albumRepository.existsById(entityId)
                EntityType.BOOK -> bookRepository.existsById(entityId)
                else -> false
            }
        if (!exists) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "mídia não encontrada")
        }
    }

    private fun normalizeBody(body: String): String {
        val normalized = body.trim()
        if (normalized.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "comentário vazio")
        }
        if (normalized.length > 10_000) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "comentário muito longo")
        }
        return normalized
    }

    private fun MediaComment.toDto(): MediaCommentDto =
        MediaCommentDto(
            id = id,
            body = body,
            commentedAt = commentedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
            edited = updatedAt.isAfter(createdAt),
        )
}
