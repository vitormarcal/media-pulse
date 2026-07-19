package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.controller.webhook.dto.PlexWebhookPayload
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.plex.PlexEventType
import dev.marcal.mediapulse.server.model.tv.TvEpisode
import dev.marcal.mediapulse.server.model.tv.TvEpisodeWatch
import dev.marcal.mediapulse.server.model.tv.TvEpisodeWatchSource
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.model.tv.TvShowTitleSource
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeRepository
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeWatchCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowTitleCrudRepository
import dev.marcal.mediapulse.server.util.FingerprintUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PlexEpisodeWatchService(
    private val tvShowRepository: TvShowRepository,
    private val tvShowTitleCrudRepository: TvShowTitleCrudRepository,
    private val tvEpisodeRepository: TvEpisodeRepository,
    private val tvEpisodeWatchCrudRepository: TvEpisodeWatchCrudRepository,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    @Transactional
    suspend fun processScrobble(payload: PlexWebhookPayload): TvEpisodeWatch? {
        val meta = payload.metadata
        if (meta.type != "episode") return null

        val eventType = requireNotNull(PlexEventType.fromType(payload.event)) { "event type is not supported: ${payload.event}" }
        if (eventType != PlexEventType.SCROBBLE) return null

        val showOriginalTitle = meta.grandparentTitle?.trim()?.ifBlank { null } ?: return null
        val showDescription = null
        val showYear = meta.parentYear ?: meta.year
        val showSlug = resolveSlug(meta.grandparentSlug)
        val seasonTitle = meta.parentTitle?.trim()?.ifBlank { null }
        val showFingerprint = FingerprintUtil.tvShowFp(originalTitle = showOriginalTitle, year = showYear)

        val show =
            tvShowRepository.findByFingerprint(showFingerprint)
                ?: tvShowRepository.save(
                    TvShow(
                        originalTitle = showOriginalTitle,
                        description = showDescription,
                        year = showYear,
                        slug = showSlug,
                        fingerprint = showFingerprint,
                    ),
                )

        if ((show.slug != showSlug && showSlug != null) || (show.year == null && showYear != null)) {
            tvShowRepository.save(
                show.copy(
                    slug = showSlug ?: show.slug,
                    year = show.year ?: showYear,
                    updatedAt = Instant.now(),
                ),
            )
        }

        tvShowTitleCrudRepository.insertIgnore(
            showId = show.id,
            title = show.originalTitle,
            locale = null,
            source = TvShowTitleSource.PLEX.name,
            isPrimary = true,
        )

        val episodeTitle = meta.title.trim()
        val episodeFingerprint =
            FingerprintUtil.tvEpisodeFp(
                showId = show.id,
                seasonNumber = meta.parentIndex,
                episodeNumber = meta.index,
                title = episodeTitle,
            )

        val episodeExternalIds = extractEpisodeExternalIds(meta.guidList)
        val resolvableEpisodeExternalIds = unambiguousExternalIds(episodeExternalIds)
        val existingEpisode =
            findEpisodeByExternalIds(resolvableEpisodeExternalIds, show.id)
                ?: tvEpisodeRepository.findByFingerprint(episodeFingerprint)
                ?: tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(show.id, meta.parentIndex, meta.index)

        val episode =
            if (existingEpisode == null) {
                tvEpisodeRepository.save(
                    TvEpisode(
                        showId = show.id,
                        title = episodeTitle,
                        seasonNumber = meta.parentIndex,
                        seasonTitle = seasonTitle,
                        episodeNumber = meta.index,
                        summary = meta.summary?.trim()?.ifBlank { null },
                        durationMs = meta.duration,
                        originallyAvailableAt = meta.originallyAvailableAt,
                        fingerprint = episodeFingerprint,
                    ),
                )
            } else {
                mergeEpisode(existingEpisode, meta, episodeTitle, episodeFingerprint, seasonTitle)
            }

        persistEpisodeExternalIds(episode, episodeExternalIds)

        val watchedAt = meta.lastViewedAt ?: Instant.now()
        tvEpisodeWatchCrudRepository.insertIgnore(
            episodeId = episode.id,
            source = TvEpisodeWatchSource.PLEX.name,
            watchedAt = watchedAt,
        )

        return TvEpisodeWatch(
            episodeId = episode.id,
            source = TvEpisodeWatchSource.PLEX,
            watchedAt = watchedAt,
        )
    }

    private fun findEpisodeByExternalIds(
        externalIds: List<Pair<Provider, String>>,
        showId: Long,
    ): TvEpisode? {
        externalIds.forEach { (provider, externalId) ->
            val episode = findEpisodeByExternalId(provider, externalId) ?: return@forEach
            if (episode.showId == showId) return episode
        }
        return null
    }

    private fun mergeEpisode(
        existing: TvEpisode,
        meta: PlexWebhookPayload.PlexMetadata,
        title: String,
        fingerprint: String,
        seasonTitle: String?,
    ): TvEpisode {
        val updatedTitle = if (existing.title.isBlank()) title else existing.title
        val updatedSeasonNumber = existing.seasonNumber ?: meta.parentIndex
        val updatedSeasonTitle = existing.seasonTitle ?: seasonTitle
        val updatedEpisodeNumber = existing.episodeNumber ?: meta.index
        val updatedSummary = meta.summary?.trim()?.ifBlank { null } ?: existing.summary
        val updatedDurationMs = meta.duration ?: existing.durationMs
        val updatedOriginallyAvailableAt = meta.originallyAvailableAt ?: existing.originallyAvailableAt
        val updatedFingerprint = if (existing.fingerprint.isBlank()) fingerprint else existing.fingerprint

        val changed =
            updatedTitle != existing.title ||
                updatedSeasonNumber != existing.seasonNumber ||
                updatedSeasonTitle != existing.seasonTitle ||
                updatedEpisodeNumber != existing.episodeNumber ||
                updatedSummary != existing.summary ||
                updatedDurationMs != existing.durationMs ||
                updatedOriginallyAvailableAt != existing.originallyAvailableAt ||
                updatedFingerprint != existing.fingerprint

        return if (changed) {
            tvEpisodeRepository.save(
                existing.copy(
                    title = updatedTitle,
                    seasonNumber = updatedSeasonNumber,
                    seasonTitle = updatedSeasonTitle,
                    episodeNumber = updatedEpisodeNumber,
                    summary = updatedSummary,
                    durationMs = updatedDurationMs,
                    originallyAvailableAt = updatedOriginallyAvailableAt,
                    fingerprint = updatedFingerprint,
                    updatedAt = Instant.now(),
                ),
            )
        } else {
            existing
        }
    }

    private fun persistEpisodeExternalIds(
        episode: TvEpisode,
        externalIds: List<Pair<Provider, String>>,
    ) {
        var updatedEpisode = episode
        externalIds.groupBy({ it.first }, { it.second }).forEach { (provider, externalIdsForProvider) ->
            if (externalIdsForProvider.size > 1) {
                logger.warn(
                    "Ignoring ambiguous Plex episode identifiers. episodeId={}, showId={}, provider={}, candidates={}",
                    episode.id,
                    episode.showId,
                    provider,
                    externalIdsForProvider,
                )
                return@forEach
            }
            val externalId = externalIdsForProvider.single()
            val currentExternalId = updatedEpisode.externalId(provider)
            if (currentExternalId == externalId) return@forEach
            if (currentExternalId != null) {
                logger.warn(
                    "Ignoring conflicting Plex episode identifier. episodeId={}, showId={}, provider={}, currentExternalId={}, incomingExternalId={}",
                    episode.id,
                    episode.showId,
                    provider,
                    currentExternalId,
                    externalId,
                )
                return@forEach
            }
            val linkedEpisode = findEpisodeByExternalId(provider, externalId)
            if (linkedEpisode != null) {
                if (linkedEpisode.id != episode.id) {
                    logger.warn(
                        "Ignoring Plex episode identifier linked to another episode. episodeId={}, showId={}, linkedEpisodeId={}, provider={}, incomingExternalId={}",
                        episode.id,
                        episode.showId,
                        linkedEpisode.id,
                        provider,
                        externalId,
                    )
                }
                return@forEach
            }
            updatedEpisode = updatedEpisode.withExternalId(provider, externalId)
        }
        if (updatedEpisode != episode) tvEpisodeRepository.save(updatedEpisode)
    }

    private fun unambiguousExternalIds(externalIds: List<Pair<Provider, String>>): List<Pair<Provider, String>> =
        externalIds
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size == 1 }
            .map { (provider, ids) -> provider to ids.single() }

    private fun extractEpisodeExternalIds(guidList: List<PlexWebhookPayload.PlexMetadata.PlexGuidMetadata>): List<Pair<Provider, String>> =
        guidList
            .mapNotNull { guid ->
                val raw = guid.id?.trim()?.ifBlank { null } ?: return@mapNotNull null
                when {
                    raw.startsWith("tmdb://", ignoreCase = true) -> Provider.TMDB to raw.substringAfter("tmdb://")
                    raw.startsWith("tvdb://", ignoreCase = true) -> Provider.TVDB to raw.substringAfter("tvdb://")
                    raw.startsWith("imdb://", ignoreCase = true) -> Provider.IMDB to raw.substringAfter("imdb://")
                    else -> null
                }
            }.map { (provider, externalId) -> provider to externalId.trim() }
            .filter { (_, externalId) -> externalId.isNotBlank() }
            .distinct()

    private fun findEpisodeByExternalId(
        provider: Provider,
        externalId: String,
    ): TvEpisode? =
        when (provider) {
            Provider.TMDB -> tvEpisodeRepository.findByTmdbId(externalId)
            Provider.TVDB -> tvEpisodeRepository.findByTvdbId(externalId)
            Provider.IMDB -> tvEpisodeRepository.findByImdbId(externalId)
            else -> null
        }

    private fun TvEpisode.externalId(provider: Provider): String? =
        when (provider) {
            Provider.TMDB -> tmdbId
            Provider.TVDB -> tvdbId
            Provider.IMDB -> imdbId
            else -> null
        }

    private fun TvEpisode.withExternalId(
        provider: Provider,
        externalId: String,
    ): TvEpisode =
        when (provider) {
            Provider.TMDB -> copy(tmdbId = externalId, updatedAt = Instant.now())
            Provider.TVDB -> copy(tvdbId = externalId, updatedAt = Instant.now())
            Provider.IMDB -> copy(imdbId = externalId, updatedAt = Instant.now())
            else -> this
        }

    private fun resolveSlug(slug: String?): String? = slug?.trim()?.ifBlank { null }
}
