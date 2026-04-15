package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.ManualMovieExternalIdView
import dev.marcal.mediapulse.server.api.movies.ManualMovieWatchCreateResponse
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.movie.MovieWatchSource
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class ExistingMovieWatchCreateFlowService(
    private val movieRepository: MovieRepository,
    private val manualMovieWatchRegistrationService: ManualMovieWatchRegistrationService,
    private val externalIdentifierRepository: ExternalIdentifierRepository,
) {
    @Transactional
    fun execute(
        movieId: Long,
        watchedAt: Instant,
    ): ManualMovieWatchCreateResponse {
        val movie =
            movieRepository.findById(movieId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found")
            }

        val watchInserted =
            manualMovieWatchRegistrationService.register(
                movieId = movieId,
                watchedAt = watchedAt,
            )

        val externalIds =
            externalIdentifierRepository
                .findByEntityTypeAndEntityId(EntityType.MOVIE, movieId)
                .sortedWith(compareBy({ it.provider.name }, { it.externalId }))
                .map { ManualMovieExternalIdView(provider = it.provider.name, externalId = it.externalId) }

        return ManualMovieWatchCreateResponse(
            movieId = movie.id,
            title = movie.originalTitle,
            year = movie.year,
            coverUrl = movie.coverUrl,
            watchedAt = watchedAt,
            source = MovieWatchSource.MANUAL.name,
            createdMovie = false,
            watchInserted = watchInserted,
            coverAssigned = false,
            externalIds = externalIds,
        )
    }
}
