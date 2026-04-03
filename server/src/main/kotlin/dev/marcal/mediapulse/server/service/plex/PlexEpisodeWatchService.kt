package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.controller.webhook.dto.PlexWebhookPayload
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.plex.PlexEventType
import dev.marcal.mediapulse.server.model.tv.TvEpisode
import dev.marcal.mediapulse.server.model.tv.TvEpisodeWatch
import dev.marcal.mediapulse.server.model.tv.TvEpisodeWatchSource
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.model.tv.TvShowTitleSource
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeRepository
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeWatchCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowTitleCrudRepository
import dev.marcal.mediapulse.server.util.FingerprintUtil
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PlexEpisodeWatchService(
    private val tvShowRepository: TvShowRepository,
    private val tvShowTitleCrudRepository: TvShowTitleCrudRepository,
    private val tvEpisodeRepository: TvEpisodeRepository,
    private val tvEpisodeWatchCrudRepository: TvEpisodeWatchCrudRepository,
    private val externalIdentifierRepository: ExternalIdentifierRepository,
) {
    @Transactional
    suspend fun processScrobble(payload: PlexWebhookPayload): TvEpisodeWatch? {
        val meta = payload.metadata
        if (meta.type != "episode") return null

        val eventType = requireNotNull(PlexEventType.fromType(payload.event)) { "event type is not supported: ${payload.event}" }
        if (eventType != PlexEventType.SCROBBLE) return null

        val showOriginalTitle =
            meta.originalTitle?.trim()?.ifBlank { null }
                ?: meta.grandparentTitle?.trim()?.ifBlank { null }
                ?: return null
        val showLocalizedTitle = meta.grandparentTitle?.trim()?.ifBlank { null }
        val showDescription = null
        val showYear = meta.parentYear ?: meta.year
        val showSlug = resolveSlug(meta.grandparentSlug)
        val showPlexGuid = meta.grandparentGuid?.trim()?.ifBlank { null }
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

        if (showLocalizedTitle != null && showLocalizedTitle != show.originalTitle) {
            tvShowTitleCrudRepository.insertIgnore(
                showId = show.id,
                title = showLocalizedTitle,
                locale = null,
                source = TvShowTitleSource.PLEX.name,
                isPrimary = false,
            )
        }

        safeLink(
            entityType = EntityType.SHOW,
            entityId = show.id,
            provider = Provider.PLEX,
            externalId = showPlexGuid,
        )

        val episodeTitle = meta.title.trim()
        val episodeFingerprint =
            FingerprintUtil.tvEpisodeFp(
                showId = show.id,
                seasonNumber = meta.parentIndex,
                episodeNumber = meta.index,
                title = episodeTitle,
            )

        val existingEpisode =
            tvEpisodeRepository.findByFingerprint(episodeFingerprint)
                ?: tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(show.id, meta.parentIndex, meta.index)

        val episode =
            if (existingEpisode == null) {
                tvEpisodeRepository.save(
                    TvEpisode(
                        showId = show.id,
                        title = episodeTitle,
                        seasonNumber = meta.parentIndex,
                        episodeNumber = meta.index,
                        summary = meta.summary?.trim()?.ifBlank { null },
                        durationMs = meta.duration,
                        originallyAvailableAt = meta.originallyAvailableAt,
                        fingerprint = episodeFingerprint,
                    ),
                )
            } else {
                mergeEpisode(existingEpisode, meta, episodeTitle, episodeFingerprint)
            }

        safeLink(
            entityType = EntityType.EPISODE,
            entityId = episode.id,
            provider = Provider.PLEX,
            externalId = meta.guid,
        )
        persistEpisodeExternalIds(episode.id, meta.guidList)

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

    private fun mergeEpisode(
        existing: TvEpisode,
        meta: PlexWebhookPayload.PlexMetadata,
        title: String,
        fingerprint: String,
    ): TvEpisode {
        val updatedTitle = if (existing.title.isBlank()) title else existing.title
        val updatedSeasonNumber = existing.seasonNumber ?: meta.parentIndex
        val updatedEpisodeNumber = existing.episodeNumber ?: meta.index
        val updatedSummary = meta.summary?.trim()?.ifBlank { null } ?: existing.summary
        val updatedDurationMs = meta.duration ?: existing.durationMs
        val updatedOriginallyAvailableAt = meta.originallyAvailableAt ?: existing.originallyAvailableAt
        val updatedFingerprint = if (existing.fingerprint.isBlank()) fingerprint else existing.fingerprint

        val changed =
            updatedTitle != existing.title ||
                updatedSeasonNumber != existing.seasonNumber ||
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
        episodeId: Long,
        guidList: List<PlexWebhookPayload.PlexMetadata.PlexGuidMetadata>,
    ) {
        guidList
            .asSequence()
            .mapNotNull { guid ->
                val raw = guid.id?.trim()?.ifBlank { null } ?: return@mapNotNull null
                when {
                    raw.startsWith("tmdb://", ignoreCase = true) -> Provider.TMDB to raw.substringAfter("tmdb://")
                    raw.startsWith("imdb://", ignoreCase = true) -> Provider.IMDB to raw.substringAfter("imdb://")
                    raw.startsWith("tvdb://", ignoreCase = true) -> Provider.TVDB to raw.substringAfter("tvdb://")
                    else -> null
                }
            }.map { (provider, externalId) -> provider to externalId.trim() }
            .filter { (_, externalId) -> externalId.isNotBlank() }
            .distinct()
            .forEach { (provider, externalId) ->
                safeLink(
                    entityType = EntityType.EPISODE,
                    entityId = episodeId,
                    provider = provider,
                    externalId = externalId,
                )
            }
    }

    private fun safeLink(
        entityType: EntityType,
        entityId: Long,
        provider: Provider,
        externalId: String?,
    ) {
        val cleanId = externalId?.trim()?.ifBlank { null } ?: return
        if (externalIdentifierRepository.findByProviderAndExternalId(provider, cleanId) != null) return

        externalIdentifierRepository.save(
            ExternalIdentifier(
                entityType = entityType,
                entityId = entityId,
                provider = provider,
                externalId = cleanId,
            ),
        )
    }

    private fun resolveSlug(slug: String?): String? = slug?.trim()?.ifBlank { null }
}
