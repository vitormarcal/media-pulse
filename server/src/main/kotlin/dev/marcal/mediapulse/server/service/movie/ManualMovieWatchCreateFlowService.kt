package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.ManualMovieExternalIdView
import dev.marcal.mediapulse.server.api.movies.ManualMovieWatchCreateRequest
import dev.marcal.mediapulse.server.api.movies.ManualMovieWatchCreateResponse
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.movie.MovieWatchSource
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ManualMovieWatchCreateFlowService(
    private val manualMovieCatalogService: ManualMovieCatalogService,
    private val manualMovieWatchRegistrationService: ManualMovieWatchRegistrationService,
    private val externalIdentifierRepository: ExternalIdentifierRepository,
) {
    @Transactional
    fun execute(request: ManualMovieWatchCreateRequest): ManualMovieWatchCreateResponse {
        val catalogResult = manualMovieCatalogService.resolveOrCreate(request)
        val watchInserted =
            manualMovieWatchRegistrationService.register(
                movieId = catalogResult.movie.id,
                watchedAt = request.watchedAt,
            )

        val externalIds =
            externalIdentifierRepository
                .findByEntityTypeAndEntityId(EntityType.MOVIE, catalogResult.movie.id)
                .sortedWith(compareBy({ it.provider.name }, { it.externalId }))
                .map { ManualMovieExternalIdView(provider = it.provider.name, externalId = it.externalId) }

        return ManualMovieWatchCreateResponse(
            movieId = catalogResult.movie.id,
            title = catalogResult.movie.originalTitle,
            year = catalogResult.movie.year,
            coverUrl = catalogResult.movie.coverUrl,
            watchedAt = request.watchedAt,
            source = MovieWatchSource.MANUAL.name,
            createdMovie = catalogResult.created,
            watchInserted = watchInserted,
            coverAssigned = catalogResult.coverAssigned,
            externalIds = externalIds,
        )
    }
}
