package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ExistingShowWatchCreateRequest
import dev.marcal.mediapulse.server.api.shows.ManualShowExternalIdView
import dev.marcal.mediapulse.server.api.shows.ManualShowWatchCreateResponse
import dev.marcal.mediapulse.server.model.tv.TvEpisode
import dev.marcal.mediapulse.server.model.tv.TvEpisodeWatchSource
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.util.FingerprintUtil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ExistingShowWatchCreateFlowService(
    private val tvShowRepository: TvShowRepository,
    private val tvEpisodeRepository: TvEpisodeRepository,
    private val manualShowWatchRegistrationService: ManualShowWatchRegistrationService,
) {
    @Transactional
    fun execute(
        showId: Long,
        request: ExistingShowWatchCreateRequest,
    ): ManualShowWatchCreateResponse {
        val show =
            tvShowRepository.findById(showId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found")
            }
        val episodeTitle =
            request.episodeTitle.trim().ifBlank {
                throw IllegalArgumentException("episodeTitle deve ser preenchido")
            }
        val episodeFingerprint =
            FingerprintUtil.tvEpisodeFp(
                showId = show.id,
                seasonNumber = request.seasonNumber,
                episodeNumber = request.episodeNumber,
                title = episodeTitle,
            )
        val existingEpisode =
            tvEpisodeRepository.findByFingerprint(episodeFingerprint)
                ?: tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(
                    show.id,
                    request.seasonNumber,
                    request.episodeNumber,
                )
        val createdEpisode = existingEpisode == null
        val episode =
            existingEpisode
                ?: tvEpisodeRepository.save(
                    TvEpisode(
                        showId = show.id,
                        title = episodeTitle,
                        seasonNumber = request.seasonNumber,
                        episodeNumber = request.episodeNumber,
                        originallyAvailableAt = request.originallyAvailableAt,
                        fingerprint = episodeFingerprint,
                    ),
                )

        val watchInserted =
            manualShowWatchRegistrationService.register(
                episodeId = episode.id,
                watchedAt = request.watchedAt,
            )
        val externalIds =
            listOfNotNull(
                show.imdbId?.let { ManualShowExternalIdView(provider = "IMDB", externalId = it) },
                show.tmdbId?.let { ManualShowExternalIdView(provider = "TMDB", externalId = it) },
                show.tvdbId?.let { ManualShowExternalIdView(provider = "TVDB", externalId = it) },
            )

        return ManualShowWatchCreateResponse(
            showId = show.id,
            title = show.originalTitle,
            year = show.year,
            coverUrl = show.coverUrl,
            episodeId = episode.id,
            episodeTitle = episode.title,
            seasonNumber = episode.seasonNumber,
            episodeNumber = episode.episodeNumber,
            watchedAt = request.watchedAt,
            source = TvEpisodeWatchSource.MANUAL.name,
            createdShow = false,
            createdEpisode = createdEpisode,
            watchInserted = watchInserted,
            coverAssigned = false,
            externalIds = externalIds,
        )
    }
}
