package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ManualShowCatalogCreateRequest
import dev.marcal.mediapulse.server.api.shows.ManualShowCatalogCreateResponse
import dev.marcal.mediapulse.server.api.shows.ManualShowExternalIdView
import dev.marcal.mediapulse.server.api.shows.ShowCatalogSuggestionDto
import dev.marcal.mediapulse.server.api.shows.ShowCatalogSuggestionsResponse
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ManualShowCatalogCreateFlowService(
    private val manualShowCatalogService: ManualShowCatalogService,
    private val externalIdentifierRepository: ExternalIdentifierRepository,
    private val tmdbApiClient: TmdbApiClient,
) {
    fun suggest(query: String): ShowCatalogSuggestionsResponse =
        ShowCatalogSuggestionsResponse(
            query = query.trim(),
            suggestions =
                tmdbApiClient
                    .searchShows(query)
                    .map { item ->
                        ShowCatalogSuggestionDto(
                            tmdbId = item.tmdbId,
                            title = item.title ?: item.originalTitle ?: item.tmdbId,
                            originalTitle = item.originalTitle,
                            year = item.firstAirYear,
                            overview = item.overview,
                            posterUrl = item.posterPath?.let { path -> manualShowCatalogService.buildTmdbImageUrl(path) },
                        )
                    },
        )

    @Transactional
    fun execute(request: ManualShowCatalogCreateRequest): ManualShowCatalogCreateResponse {
        val catalogResult =
            manualShowCatalogService.resolveOrCreateCatalog(
                ManualShowCatalogService.ShowCatalogUpsertRequest(
                    title = request.title,
                    year = request.year,
                    tmdbId = request.tmdbId,
                    tvdbId = request.tvdbId,
                    importEpisodes = request.importEpisodes,
                ),
            )

        val externalIds =
            externalIdentifierRepository
                .findByEntityTypeAndEntityId(EntityType.SHOW, catalogResult.show.id)
                .sortedWith(compareBy({ it.provider.name }, { it.externalId }))
                .map { ManualShowExternalIdView(provider = it.provider.name, externalId = it.externalId) }

        return ManualShowCatalogCreateResponse(
            showId = catalogResult.show.id,
            slug = catalogResult.show.slug,
            title = catalogResult.show.originalTitle,
            year = catalogResult.show.year,
            coverUrl = catalogResult.show.coverUrl,
            createdShow = catalogResult.createdShow,
            coverAssigned = catalogResult.coverAssigned,
            seasonsImported = catalogResult.seasonsImported,
            episodesImported = catalogResult.episodesImported,
            externalIds = externalIds,
        )
    }
}
