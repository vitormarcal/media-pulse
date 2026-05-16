package dev.marcal.mediapulse.server.service.rating

import dev.marcal.mediapulse.server.api.ratings.MediaRatingDto
import dev.marcal.mediapulse.server.api.ratings.UpsertMediaRatingRequest
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.rating.MediaRating
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.repository.crud.GameRepository
import dev.marcal.mediapulse.server.repository.crud.MediaRatingRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.repository.crud.TrackRepository
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class MediaRatingService(
    private val mediaRatingRepository: MediaRatingRepository,
    private val movieRepository: MovieRepository,
    private val tvShowRepository: TvShowRepository,
    private val tvEpisodeRepository: TvEpisodeRepository,
    private val albumRepository: AlbumRepository,
    private val trackRepository: TrackRepository,
    private val gameRepository: GameRepository,
) {
    @Transactional
    fun upsert(
        mediaType: String,
        entityId: Long,
        request: UpsertMediaRatingRequest,
    ): MediaRatingDto {
        val entityType = resolveMediaType(mediaType)
        ensureEntityExists(entityType, entityId)
        validateRating(request.rating)
        val now = Instant.now()
        val current = mediaRatingRepository.findByEntityTypeAndEntityId(entityType, entityId)
        val saved =
            mediaRatingRepository.save(
                if (current == null) {
                    MediaRating(
                        entityType = entityType,
                        entityId = entityId,
                        rating = request.rating.toShort(),
                        createdAt = now,
                        updatedAt = now,
                    )
                } else {
                    current.copy(
                        rating = request.rating.toShort(),
                        updatedAt = now,
                    )
                },
            )
        return saved.toDto()
    }

    @Transactional
    fun clear(
        mediaType: String,
        entityId: Long,
    ) {
        val entityType = resolveMediaType(mediaType)
        ensureEntityExists(entityType, entityId)
        mediaRatingRepository.deleteByEntityTypeAndEntityId(entityType, entityId)
    }

    private fun resolveMediaType(mediaType: String): EntityType =
        when (mediaType.trim().lowercase()) {
            "movies" -> EntityType.MOVIE
            "shows" -> EntityType.SHOW
            "episodes" -> EntityType.EPISODE
            "albums" -> EntityType.ALBUM
            "tracks" -> EntityType.TRACK
            "games" -> EntityType.GAME
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
                EntityType.EPISODE -> tvEpisodeRepository.existsById(entityId)
                EntityType.ALBUM -> albumRepository.existsById(entityId)
                EntityType.TRACK -> trackRepository.existsById(entityId)
                EntityType.GAME -> gameRepository.existsById(entityId)
                else -> false
            }
        if (!exists) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "mídia não encontrada")
        }
    }

    private fun validateRating(rating: Int) {
        if (rating !in 1..5) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "nota deve estar entre 1 e 5")
        }
    }

    private fun MediaRating.toDto(): MediaRatingDto =
        MediaRatingDto(
            rating = rating.toInt(),
            updatedAt = updatedAt,
        )
}
