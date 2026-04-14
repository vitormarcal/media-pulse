package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.ManualMovieCatalogCreateRequest
import dev.marcal.mediapulse.server.api.movies.ManualMovieCatalogCreateResponse
import dev.marcal.mediapulse.server.api.movies.ManualMovieExternalIdView
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ManualMovieCatalogCreateFlowService(
    private val manualMovieCatalogService: ManualMovieCatalogService,
    private val externalIdentifierRepository: ExternalIdentifierRepository,
) {
    @Transactional
    fun execute(request: ManualMovieCatalogCreateRequest): ManualMovieCatalogCreateResponse {
        val catalogResult =
            manualMovieCatalogService.resolveOrCreate(
                ManualMovieCatalogService.MovieCatalogUpsertRequest(
                    title = request.title,
                    year = request.year,
                    tmdbId = request.tmdbId,
                    imdbId = request.imdbId,
                ),
            )

        val externalIds =
            externalIdentifierRepository
                .findByEntityTypeAndEntityId(EntityType.MOVIE, catalogResult.movie.id)
                .sortedWith(compareBy({ it.provider.name }, { it.externalId }))
                .map { ManualMovieExternalIdView(provider = it.provider.name, externalId = it.externalId) }

        return ManualMovieCatalogCreateResponse(
            movieId = catalogResult.movie.id,
            slug = catalogResult.movie.slug,
            title = catalogResult.movie.originalTitle,
            year = catalogResult.movie.year,
            coverUrl = catalogResult.movie.coverUrl,
            createdMovie = catalogResult.created,
            coverAssigned = catalogResult.coverAssigned,
            externalIds = externalIds,
        )
    }
}
