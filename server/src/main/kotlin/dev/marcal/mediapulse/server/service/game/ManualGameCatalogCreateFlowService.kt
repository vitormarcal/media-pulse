package dev.marcal.mediapulse.server.service.game

import dev.marcal.mediapulse.server.api.games.GameCatalogSuggestionDto
import dev.marcal.mediapulse.server.api.games.GameCatalogSuggestionsResponse
import dev.marcal.mediapulse.server.api.games.ManualGameCatalogCreateRequest
import dev.marcal.mediapulse.server.api.games.ManualGameCatalogCreateResponse
import dev.marcal.mediapulse.server.api.games.ManualGameExternalIdView
import dev.marcal.mediapulse.server.integration.igdb.IgdbApiClient
import dev.marcal.mediapulse.server.integration.igdb.IgdbGameResponse
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset

@Service
class ManualGameCatalogCreateFlowService(
    private val manualGameCatalogService: ManualGameCatalogService,
    private val externalIdentifierRepository: ExternalIdentifierRepository,
    private val igdbApiClient: IgdbApiClient,
) {
    fun suggest(query: String): GameCatalogSuggestionsResponse =
        GameCatalogSuggestionsResponse(
            query = query.trim(),
            suggestions =
                igdbApiClient
                    .searchGames(query)
                    .map { item ->
                        GameCatalogSuggestionDto(
                            igdbId = item.id.toString(),
                            title = item.name ?: item.id.toString(),
                            year = item.releaseYear(),
                            overview = item.summary ?: item.storyline,
                            coverUrl = manualGameCatalogService.buildIgdbCoverUrl(item),
                        )
                    },
        )

    @Transactional
    fun execute(request: ManualGameCatalogCreateRequest): ManualGameCatalogCreateResponse {
        val catalogResult =
            manualGameCatalogService.resolveOrCreate(
                ManualGameCatalogService.GameCatalogUpsertRequest(
                    title = request.title,
                    year = request.year,
                    igdbId = request.igdbId,
                ),
            )

        val externalIds =
            externalIdentifierRepository
                .findByEntityTypeAndEntityId(EntityType.GAME, catalogResult.game.id)
                .sortedWith(compareBy({ it.provider.name }, { it.externalId }))
                .map { ManualGameExternalIdView(provider = it.provider.name, externalId = it.externalId) }

        return ManualGameCatalogCreateResponse(
            gameId = catalogResult.game.id,
            slug = catalogResult.game.slug,
            title = catalogResult.game.title,
            year = catalogResult.game.year,
            coverUrl = catalogResult.game.coverUrl,
            createdGame = catalogResult.created,
            coverAssigned = catalogResult.coverAssigned,
            externalIds = externalIds,
        )
    }

    private fun IgdbGameResponse.releaseYear(): Int? =
        firstReleaseDate
            ?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).year }
}
