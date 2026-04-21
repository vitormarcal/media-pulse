package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ShowSeasonEnrichmentApplyMode
import dev.marcal.mediapulse.server.api.shows.ShowSeasonEnrichmentApplyRequest
import dev.marcal.mediapulse.server.api.shows.ShowSeasonEnrichmentApplyResponse
import dev.marcal.mediapulse.server.api.shows.ShowSeasonEnrichmentField
import dev.marcal.mediapulse.server.api.shows.ShowSeasonEnrichmentFieldPreview
import dev.marcal.mediapulse.server.api.shows.ShowSeasonEnrichmentPreviewRequest
import dev.marcal.mediapulse.server.api.shows.ShowSeasonEnrichmentPreviewResponse
import dev.marcal.mediapulse.server.api.shows.ShowSeasonEpisodeEnrichmentPreview
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.tv.TvEpisode
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate

@Service
class ShowSeasonMetadataEnrichmentService(
    private val tvShowRepository: TvShowRepository,
    private val tvEpisodeRepository: TvEpisodeRepository,
    private val externalIdentifierRepository: ExternalIdentifierRepository,
    private val tmdbApiClient: TmdbApiClient,
) {
    @Transactional(readOnly = true)
    fun preview(
        showId: Long,
        seasonNumber: Int,
        request: ShowSeasonEnrichmentPreviewRequest,
    ): ShowSeasonEnrichmentPreviewResponse {
        val show =
            tvShowRepository.findById(showId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found")
            }
        val episodes = loadSeasonEpisodes(showId, seasonNumber)
        val tmdbId = resolveTmdbId(showId, request.tmdbId)
        val tmdbSeason =
            tmdbApiClient.fetchShowSeasonDetails(tmdbId, seasonNumber)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb season details not found")
        val tmdbEpisodesByNumber =
            tmdbSeason.episodes
                .mapNotNull { tmdbEpisode ->
                    tmdbEpisode.episodeNumber?.let { it to tmdbEpisode }
                }.toMap()
        val currentSeasonTitle = episodes.firstNotNullOfOrNull { it.seasonTitle?.trim()?.ifBlank { null } }
        val seasonFields =
            listOf(
                previewField(
                    field = ShowSeasonEnrichmentField.SEASON_TITLE,
                    label = "Nome da temporada",
                    currentValue = currentSeasonTitle,
                    suggestedValue = tmdbSeason.title,
                    missing = isGenericSeasonTitle(currentSeasonTitle, seasonNumber),
                ),
            )
        val episodePreviews =
            episodes.map { episode ->
                val tmdbEpisode = episode.episodeNumber?.let { tmdbEpisodesByNumber[it] }
                buildEpisodePreview(episode, tmdbEpisode)
            }
        val localEpisodeNumbers = episodes.mapNotNull { it.episodeNumber }.toSet()
        val missingTmdbEpisodesCount = tmdbEpisodesByNumber.keys.count { it !in localEpisodeNumbers }

        return ShowSeasonEnrichmentPreviewResponse(
            showId = showId,
            seasonNumber = seasonNumber,
            resolvedTmdbId = tmdbId,
            showTitle = show.originalTitle,
            seasonTitle = currentSeasonTitle,
            suggestedSeasonTitle = tmdbSeason.title,
            seasonFields = seasonFields,
            episodes = episodePreviews,
            changedEpisodesCount = episodePreviews.count { preview -> preview.fields.any { it.changed && it.available } },
            selectedFieldsCount = (seasonFields + episodePreviews.flatMap { it.fields }).count { it.selectedByDefault },
            missingTmdbEpisodesCount = missingTmdbEpisodesCount,
        )
    }

    @Transactional
    fun apply(
        showId: Long,
        seasonNumber: Int,
        request: ShowSeasonEnrichmentApplyRequest,
    ): ShowSeasonEnrichmentApplyResponse {
        if (!tvShowRepository.existsById(showId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found")
        }

        val episodes = loadSeasonEpisodes(showId, seasonNumber)
        val tmdbId = resolveTmdbId(showId, request.tmdbId)
        val tmdbSeason =
            tmdbApiClient.fetchShowSeasonDetails(tmdbId, seasonNumber)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb season details not found")
        val tmdbEpisodesByNumber =
            tmdbSeason.episodes
                .mapNotNull { tmdbEpisode ->
                    tmdbEpisode.episodeNumber?.let { it to tmdbEpisode }
                }.toMap()
        val selectedByEpisodeId = request.episodeFields.associate { it.episodeId to it.fields.toSet() }
        val selectedSeasonFields = request.seasonFields.toSet()
        val currentSeasonTitle = episodes.firstNotNullOfOrNull { it.seasonTitle?.trim()?.ifBlank { null } }
        val shouldApplySeasonTitle =
            shouldApply(
                mode = request.mode,
                selectedFields = selectedSeasonFields,
                field = ShowSeasonEnrichmentField.SEASON_TITLE,
                missing = isGenericSeasonTitle(currentSeasonTitle, seasonNumber),
            ) &&
                !tmdbSeason.title.isNullOrBlank() &&
                tmdbSeason.title != currentSeasonTitle

        var updatedEpisodesCount = 0
        var appliedFieldsCount = 0
        val now = Instant.now()

        episodes.forEach { episode ->
            val tmdbEpisode = episode.episodeNumber?.let { tmdbEpisodesByNumber[it] }
            var mutableEpisode = episode
            var changed = false

            if (shouldApplySeasonTitle) {
                mutableEpisode = mutableEpisode.copy(seasonTitle = tmdbSeason.title, updatedAt = now)
                changed = true
                appliedFieldsCount++
            }

            if (tmdbEpisode != null) {
                val selectedFields = selectedByEpisodeId[episode.id].orEmpty()

                if (shouldApplyEpisodeField(request.mode, selectedFields, ShowSeasonEnrichmentField.EPISODE_TITLE, episode) &&
                    !tmdbEpisode.title.isNullOrBlank() &&
                    tmdbEpisode.title != episode.title
                ) {
                    mutableEpisode = mutableEpisode.copy(title = tmdbEpisode.title, updatedAt = now)
                    changed = true
                    appliedFieldsCount++
                }

                if (shouldApplyEpisodeField(request.mode, selectedFields, ShowSeasonEnrichmentField.EPISODE_SUMMARY, episode) &&
                    !tmdbEpisode.overview.isNullOrBlank() &&
                    tmdbEpisode.overview != episode.summary
                ) {
                    mutableEpisode = mutableEpisode.copy(summary = tmdbEpisode.overview, updatedAt = now)
                    changed = true
                    appliedFieldsCount++
                }

                val suggestedDurationMs = tmdbEpisode.runtimeMinutes?.let { it * 60_000 }
                if (shouldApplyEpisodeField(request.mode, selectedFields, ShowSeasonEnrichmentField.EPISODE_DURATION, episode) &&
                    suggestedDurationMs != null &&
                    suggestedDurationMs != episode.durationMs
                ) {
                    mutableEpisode = mutableEpisode.copy(durationMs = suggestedDurationMs, updatedAt = now)
                    changed = true
                    appliedFieldsCount++
                }

                val suggestedAirDate = parseLocalDate(tmdbEpisode.airDate)
                if (shouldApplyEpisodeField(request.mode, selectedFields, ShowSeasonEnrichmentField.EPISODE_AIR_DATE, episode) &&
                    suggestedAirDate != null &&
                    suggestedAirDate != episode.originallyAvailableAt
                ) {
                    mutableEpisode = mutableEpisode.copy(originallyAvailableAt = suggestedAirDate, updatedAt = now)
                    changed = true
                    appliedFieldsCount++
                }
            }

            if (changed) {
                tvEpisodeRepository.save(mutableEpisode)
                updatedEpisodesCount++
            }
        }

        linkTmdbShowIdIfNeeded(showId, tmdbId)

        return ShowSeasonEnrichmentApplyResponse(
            showId = showId,
            seasonNumber = seasonNumber,
            resolvedTmdbId = tmdbId,
            updatedEpisodesCount = updatedEpisodesCount,
            appliedFieldsCount = appliedFieldsCount,
        )
    }

    private fun loadSeasonEpisodes(
        showId: Long,
        seasonNumber: Int,
    ): List<TvEpisode> {
        val episodes = tvEpisodeRepository.findByShowIdAndSeasonNumberOrderByEpisodeNumberAscIdAsc(showId, seasonNumber)
        if (episodes.isEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Season episodes not found")
        }
        return episodes
    }

    private fun buildEpisodePreview(
        episode: TvEpisode,
        tmdbEpisode: TmdbApiClient.TmdbShowSeasonEpisode?,
    ): ShowSeasonEpisodeEnrichmentPreview {
        val durationMs = episode.durationMs

        return ShowSeasonEpisodeEnrichmentPreview(
            episodeId = episode.id,
            episodeNumber = episode.episodeNumber,
            currentTitle = episode.title,
            suggestedTitle = tmdbEpisode?.title,
            fields =
                listOf(
                    previewField(
                        field = ShowSeasonEnrichmentField.EPISODE_TITLE,
                        label = "Título",
                        currentValue = episode.title,
                        suggestedValue = tmdbEpisode?.title,
                        missing = isGenericEpisodeTitle(episode.title, episode.episodeNumber),
                    ),
                    previewField(
                        field = ShowSeasonEnrichmentField.EPISODE_SUMMARY,
                        label = "Descrição",
                        currentValue = episode.summary,
                        suggestedValue = tmdbEpisode?.overview,
                        missing = episode.summary.isNullOrBlank(),
                    ),
                    previewField(
                        field = ShowSeasonEnrichmentField.EPISODE_DURATION,
                        label = "Duração",
                        currentValue = durationMs?.let(::formatDuration),
                        suggestedValue = tmdbEpisode?.runtimeMinutes?.let { "$it min" },
                        missing = durationMs == null || durationMs <= 0,
                    ),
                    previewField(
                        field = ShowSeasonEnrichmentField.EPISODE_AIR_DATE,
                        label = "Exibição",
                        currentValue = episode.originallyAvailableAt?.toString(),
                        suggestedValue = tmdbEpisode?.airDate,
                        missing = episode.originallyAvailableAt == null,
                    ),
                ),
        )
    }

    private fun resolveTmdbId(
        showId: Long,
        requestTmdbId: String?,
    ): String =
        requestTmdbId?.trim()?.ifBlank { null }
            ?: externalIdentifierRepository
                .findFirstByEntityTypeAndProviderAndEntityId(EntityType.SHOW, Provider.TMDB, showId)
                ?.externalId
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "tmdbId é obrigatório quando a série ainda não tem vínculo TMDb")

    private fun linkTmdbShowIdIfNeeded(
        showId: Long,
        tmdbId: String,
    ) {
        val current = externalIdentifierRepository.findFirstByEntityTypeAndProviderAndEntityId(EntityType.SHOW, Provider.TMDB, showId)
        if (current != null) return

        val linkedElsewhere = externalIdentifierRepository.findByProviderAndExternalId(Provider.TMDB, tmdbId)
        if (linkedElsewhere != null) return

        externalIdentifierRepository.save(
            ExternalIdentifier(
                entityType = EntityType.SHOW,
                entityId = showId,
                provider = Provider.TMDB,
                externalId = tmdbId,
            ),
        )
    }

    private fun shouldApplyEpisodeField(
        mode: ShowSeasonEnrichmentApplyMode,
        selectedFields: Set<ShowSeasonEnrichmentField>,
        field: ShowSeasonEnrichmentField,
        episode: TvEpisode,
    ): Boolean {
        val durationMs = episode.durationMs
        val missing =
            when (field) {
                ShowSeasonEnrichmentField.EPISODE_TITLE -> isGenericEpisodeTitle(episode.title, episode.episodeNumber)
                ShowSeasonEnrichmentField.EPISODE_SUMMARY -> episode.summary.isNullOrBlank()
                ShowSeasonEnrichmentField.EPISODE_DURATION -> durationMs == null || durationMs <= 0
                ShowSeasonEnrichmentField.EPISODE_AIR_DATE -> episode.originallyAvailableAt == null
                ShowSeasonEnrichmentField.SEASON_TITLE -> isGenericSeasonTitle(episode.seasonTitle, episode.seasonNumber)
            }
        return shouldApply(mode, selectedFields, field, missing)
    }

    private fun shouldApply(
        mode: ShowSeasonEnrichmentApplyMode,
        selectedFields: Set<ShowSeasonEnrichmentField>,
        field: ShowSeasonEnrichmentField,
        missing: Boolean,
    ): Boolean =
        when (mode) {
            ShowSeasonEnrichmentApplyMode.MISSING -> missing
            ShowSeasonEnrichmentApplyMode.SELECTED -> selectedFields.contains(field)
        }

    private fun previewField(
        field: ShowSeasonEnrichmentField,
        label: String,
        currentValue: String?,
        suggestedValue: String?,
        missing: Boolean,
    ): ShowSeasonEnrichmentFieldPreview {
        val normalizedSuggested = suggestedValue?.trim()?.ifBlank { null }
        val available = normalizedSuggested != null
        val changed = available && currentValue?.trim() != normalizedSuggested
        return ShowSeasonEnrichmentFieldPreview(
            field = field,
            label = label,
            currentValue = currentValue,
            suggestedValue = normalizedSuggested,
            available = available,
            missing = missing,
            changed = changed,
            selectedByDefault = available && changed && missing,
        )
    }

    private fun isGenericEpisodeTitle(
        title: String?,
        episodeNumber: Int?,
    ): Boolean {
        val value = title?.trim()?.ifBlank { return true } ?: return true
        if (episodeNumber == null) return false
        val escapedNumber = Regex.escape(episodeNumber.toString())
        val pattern = Regex("""(?i)^(episode|epis[oó]dio|ep\.?)\s*0*$escapedNumber$""")
        return pattern.matches(value)
    }

    private fun isGenericSeasonTitle(
        title: String?,
        seasonNumber: Int?,
    ): Boolean {
        val value = title?.trim()?.ifBlank { return true } ?: return true
        if (seasonNumber == null) return false
        val escapedNumber = Regex.escape(seasonNumber.toString())
        val pattern = Regex("""(?i)^(season|temporada)\s*0*$escapedNumber$""")
        return pattern.matches(value)
    }

    private fun formatDuration(durationMs: Int): String = "${durationMs / 60_000} min"

    private fun parseLocalDate(value: String?): LocalDate? {
        val normalized = value?.trim()?.ifBlank { null } ?: return null
        return runCatching { LocalDate.parse(normalized) }.getOrNull()
    }
}
