package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ManualShowExternalIdView
import dev.marcal.mediapulse.server.api.shows.ManualShowWatchCreateRequest
import dev.marcal.mediapulse.server.api.shows.ManualShowWatchCreateResponse
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.tv.TvEpisodeWatchSource
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ManualShowWatchCreateFlowService(
    private val manualShowCatalogService: ManualShowCatalogService,
    private val manualShowWatchRegistrationService: ManualShowWatchRegistrationService,
    private val externalIdentifierRepository: ExternalIdentifierRepository,
) {
    @Transactional
    fun execute(request: ManualShowWatchCreateRequest): ManualShowWatchCreateResponse {
        val catalogResult = manualShowCatalogService.resolveOrCreate(request)
        val watchInserted =
            manualShowWatchRegistrationService.register(
                episodeId = catalogResult.episode.id,
                watchedAt = request.watchedAt,
            )

        val externalIds =
            externalIdentifierRepository
                .findByEntityTypeAndEntityId(EntityType.SHOW, catalogResult.show.id)
                .sortedWith(compareBy({ it.provider.name }, { it.externalId }))
                .map { ManualShowExternalIdView(provider = it.provider.name, externalId = it.externalId) }

        return ManualShowWatchCreateResponse(
            showId = catalogResult.show.id,
            title = catalogResult.show.originalTitle,
            year = catalogResult.show.year,
            coverUrl = catalogResult.show.coverUrl,
            episodeId = catalogResult.episode.id,
            episodeTitle = catalogResult.episode.title,
            seasonNumber = catalogResult.episode.seasonNumber,
            episodeNumber = catalogResult.episode.episodeNumber,
            watchedAt = request.watchedAt,
            source = TvEpisodeWatchSource.MANUAL.name,
            createdShow = catalogResult.createdShow,
            createdEpisode = catalogResult.createdEpisode,
            watchInserted = watchInserted,
            coverAssigned = catalogResult.coverAssigned,
            externalIds = externalIds,
        )
    }
}
