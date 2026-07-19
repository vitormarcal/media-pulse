package dev.marcal.mediapulse.server.service.plex.import

import dev.marcal.mediapulse.server.integration.plex.PlexApiClient
import dev.marcal.mediapulse.server.integration.plex.dto.PlexEpisode
import dev.marcal.mediapulse.server.integration.plex.dto.PlexGuid
import dev.marcal.mediapulse.server.integration.plex.dto.PlexLibrarySection
import dev.marcal.mediapulse.server.integration.plex.dto.PlexShow
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.tv.TvEpisode
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.model.tv.TvShowTitleSource
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowTitleCrudRepository
import dev.marcal.mediapulse.server.service.plex.PlexShowArtworkService
import dev.marcal.mediapulse.server.util.FingerprintUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PlexShowImportService(
    private val plexApiClient: PlexApiClient,
    private val tvShowRepository: TvShowRepository,
    private val tvShowTitleCrudRepository: TvShowTitleCrudRepository,
    private val tvEpisodeRepository: TvEpisodeRepository,
    private val externalIdentifierRepository: ExternalIdentifierRepository,
    private val plexShowArtworkService: PlexShowArtworkService,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    data class ImportStats(
        var showsSeen: Int = 0,
        var showsUpserted: Int = 0,
        var episodesSeen: Int = 0,
        var episodesUpserted: Int = 0,
    )

    suspend fun importAllShows(
        sectionKey: String? = null,
        pageSize: Int = 200,
    ): ImportStats {
        val stats = ImportStats()

        logger.info(
            "Starting Plex show library import. sectionKey={}, pageSize={}",
            sectionKey ?: "<all-sections>",
            pageSize,
        )

        val sections =
            if (sectionKey != null) {
                listOf(PlexLibrarySection(key = sectionKey, type = "show"))
            } else {
                plexApiClient.listShowSections()
            }

        for (section in sections) {
            var start = 0
            var total = Int.MAX_VALUE
            while (start < total) {
                val (shows, tot) = plexApiClient.listShowsPaged(section.key, start, pageSize)
                total = tot
                if (shows.isEmpty()) break

                for (show in shows) {
                    stats.showsSeen++
                    val persistedShow = upsertShow(show)
                    plexShowArtworkService.ensureShowImagesFromPlex(
                        show = persistedShow,
                        images =
                            show.image.map { img ->
                                PlexShowArtworkService.PlexShowImageCandidate(
                                    url = img.url,
                                    isPoster = img.type.equals("coverPoster", ignoreCase = true),
                                )
                            },
                        fallbackThumbPath = show.thumb,
                    )
                    stats.showsUpserted++

                    importEpisodesForShow(
                        sectionKey = section.key,
                        showRatingKey = show.ratingKey,
                        show = persistedShow,
                        stats = stats,
                        pageSize = pageSize,
                    )
                }

                start += shows.size
            }
        }

        logger.info(
            "Finished Plex show library import. showsSeen={}, showsUpserted={}, episodesSeen={}, episodesUpserted={}",
            stats.showsSeen,
            stats.showsUpserted,
            stats.episodesSeen,
            stats.episodesUpserted,
        )

        return stats
    }

    private suspend fun importEpisodesForShow(
        sectionKey: String,
        showRatingKey: String,
        show: TvShow,
        stats: ImportStats,
        pageSize: Int,
    ) {
        var start = 0
        var total = Int.MAX_VALUE
        while (start < total) {
            val (episodes, tot) = plexApiClient.listEpisodesByShowPaged(sectionKey, showRatingKey, start, pageSize)
            total = tot
            if (episodes.isEmpty()) break

            for (episode in episodes) {
                stats.episodesSeen++
                upsertEpisode(show, episode)
                stats.episodesUpserted++
            }

            start += episodes.size
        }
    }

    @Transactional
    fun upsertShow(show: PlexShow): TvShow {
        val normalizedOriginal = show.originalTitle?.trim()?.ifBlank { null } ?: show.title.trim()
        val normalizedTitle = show.title.trim()
        val normalizedSummary = show.summary?.trim()?.ifBlank { null }
        val normalizedSlug = resolveSlug(show.slug)
        val normalizedYear = show.year
        val showExternalIds = extractShowExternalIds(show.guids.orEmpty())
        val existingByTmdb = showExternalIds.firstOrNull { it.first == Provider.TMDB }?.let { findShowByExternalId(it.first, it.second) }
        val existingByTvdb =
            if (existingByTmdb ==
                null
            ) {
                showExternalIds.firstOrNull { it.first == Provider.TVDB }?.let { findShowByExternalId(it.first, it.second) }
            } else {
                null
            }
        val fingerprint = FingerprintUtil.tvShowFp(normalizedOriginal, show.year)

        val existing =
            existingByTmdb
                ?: existingByTvdb
                ?: tvShowRepository.findByFingerprint(fingerprint)

        val persistedShow =
            if (existing == null) {
                tvShowRepository.save(
                    TvShow(
                        originalTitle = normalizedOriginal,
                        description = normalizedSummary,
                        year = normalizedYear,
                        coverUrl = null,
                        slug = normalizedSlug,
                        fingerprint = fingerprint,
                    ),
                )
            } else {
                val merged =
                    mergeShow(
                        existing = existing,
                        incomingYear = normalizedYear,
                        incomingDescription = normalizedSummary,
                        incomingSlug = normalizedSlug,
                    )
                if (merged != existing) tvShowRepository.save(merged) else existing
            }

        tvShowTitleCrudRepository.insertIgnore(
            showId = persistedShow.id,
            title = persistedShow.originalTitle,
            locale = null,
            source = TvShowTitleSource.PLEX.name,
            isPrimary = true,
        )

        if (normalizedTitle != persistedShow.originalTitle) {
            tvShowTitleCrudRepository.insertIgnore(
                showId = persistedShow.id,
                title = normalizedTitle,
                locale = null,
                source = TvShowTitleSource.PLEX.name,
                isPrimary = false,
            )
        }

        persistShowExternalIds(persistedShow.id, showExternalIds)

        return persistedShow
    }

    @Transactional
    fun upsertEpisode(
        show: TvShow,
        episode: PlexEpisode,
    ): TvEpisode {
        val title = episode.title.trim()
        val seasonTitle = episode.parentTitle?.trim()?.ifBlank { null }
        val fingerprint =
            FingerprintUtil.tvEpisodeFp(
                showId = show.id,
                seasonNumber = episode.parentIndex,
                episodeNumber = episode.index,
                title = title,
            )

        val episodeExternalIds = extractEpisodeExternalIds(episode.guids.orEmpty())
        val existingEpisode =
            findEpisodeByExternalIds(episodeExternalIds, show.id)
                ?: tvEpisodeRepository.findByFingerprint(fingerprint)
                ?: tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(show.id, episode.parentIndex, episode.index)

        val persistedEpisode =
            if (existingEpisode == null) {
                tvEpisodeRepository.save(
                    TvEpisode(
                        showId = show.id,
                        title = title,
                        seasonNumber = episode.parentIndex,
                        seasonTitle = seasonTitle,
                        episodeNumber = episode.index,
                        summary = episode.summary?.trim()?.ifBlank { null },
                        durationMs = episode.duration,
                        originallyAvailableAt = episode.originallyAvailableAt,
                        fingerprint = fingerprint,
                    ),
                )
            } else {
                val merged = mergeEpisode(existingEpisode, episode, title, fingerprint, seasonTitle)
                if (merged != existingEpisode) tvEpisodeRepository.save(merged) else existingEpisode
            }

        persistEpisodeExternalIds(persistedEpisode.id, episodeExternalIds)

        return persistedEpisode
    }

    private fun mergeShow(
        existing: TvShow,
        incomingYear: Int?,
        incomingDescription: String?,
        incomingSlug: String?,
    ): TvShow {
        val updatedYear = existing.year ?: incomingYear
        val updatedDescription = incomingDescription ?: existing.description
        val updatedSlug = incomingSlug ?: existing.slug
        val changed = updatedYear != existing.year || updatedDescription != existing.description || updatedSlug != existing.slug

        return if (changed) {
            existing.copy(
                year = updatedYear,
                description = updatedDescription,
                slug = updatedSlug,
                updatedAt = Instant.now(),
            )
        } else {
            existing
        }
    }

    private fun mergeEpisode(
        existing: TvEpisode,
        incoming: PlexEpisode,
        title: String,
        fingerprint: String,
        seasonTitle: String?,
    ): TvEpisode {
        val updatedTitle = if (existing.title.isBlank()) title else existing.title
        val updatedSeasonNumber = existing.seasonNumber ?: incoming.parentIndex
        val updatedSeasonTitle = existing.seasonTitle ?: seasonTitle
        val updatedEpisodeNumber = existing.episodeNumber ?: incoming.index
        val updatedSummary = incoming.summary?.trim()?.ifBlank { null } ?: existing.summary
        val updatedDurationMs = incoming.duration ?: existing.durationMs
        val updatedOriginallyAvailableAt = incoming.originallyAvailableAt ?: existing.originallyAvailableAt
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
            )
        } else {
            existing
        }
    }

    private fun findShowByExternalId(
        provider: Provider,
        externalId: String,
    ): TvShow? =
        when (provider) {
            Provider.TMDB -> tvShowRepository.findByTmdbId(externalId)
            Provider.TVDB -> tvShowRepository.findByTvdbId(externalId)
            Provider.IMDB -> tvShowRepository.findByImdbId(externalId)
            else -> null
        }

    private fun findEpisodeByExternalIds(
        externalIds: List<Pair<Provider, String>>,
        showId: Long,
    ): TvEpisode? {
        externalIds.forEach { (provider, externalId) ->
            val identifier =
                externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(
                    entityType = EntityType.EPISODE,
                    provider = provider,
                    externalId = externalId,
                ) ?: return@forEach
            val episode = tvEpisodeRepository.findById(identifier.entityId).orElse(null) ?: return@forEach
            if (episode.showId == showId) return episode
        }
        return null
    }

    private fun persistShowExternalIds(
        showId: Long,
        externalIds: List<Pair<Provider, String>>,
    ) {
        externalIds.forEach { (provider, externalId) ->
            linkShowExternalId(showId, provider, externalId)
        }
    }

    private fun linkShowExternalId(
        showId: Long,
        provider: Provider,
        externalId: String,
    ) {
        val show = tvShowRepository.findById(showId).orElse(null) ?: return
        val currentExternalId =
            when (provider) {
                Provider.TMDB -> show.tmdbId
                Provider.TVDB -> show.tvdbId
                Provider.IMDB -> show.imdbId
                else -> return
            }
        if (currentExternalId == externalId) return
        if (currentExternalId != null) {
            logger.warn(
                "Ignoring conflicting Plex show identifier. showId={}, provider={}, currentExternalId={}, incomingExternalId={}",
                showId,
                provider,
                currentExternalId,
                externalId,
            )
            return
        }

        val linkedShow = findShowByExternalId(provider, externalId)
        if (linkedShow != null) {
            logger.warn(
                "Ignoring Plex show identifier linked to another show. showId={}, linkedShowId={}, provider={}, externalId={}",
                showId,
                linkedShow.id,
                provider,
                externalId,
            )
            return
        }

        tvShowRepository.save(
            when (provider) {
                Provider.TMDB -> show.copy(tmdbId = externalId, updatedAt = Instant.now())
                Provider.TVDB -> show.copy(tvdbId = externalId, updatedAt = Instant.now())
                Provider.IMDB -> show.copy(imdbId = externalId, updatedAt = Instant.now())
                else -> show
            },
        )
    }

    private fun persistEpisodeExternalIds(
        episodeId: Long,
        externalIds: List<Pair<Provider, String>>,
    ) {
        externalIds.forEach { (provider, externalId) ->
            safeLink(
                entityType = EntityType.EPISODE,
                entityId = episodeId,
                provider = provider,
                externalId = externalId,
            )
        }
    }

    private fun extractEpisodeExternalIds(guids: List<PlexGuid>): List<Pair<Provider, String>> =
        guids
            .mapNotNull { guid ->
                val raw = guid.id.trim()
                when {
                    raw.startsWith("tmdb://", ignoreCase = true) -> Provider.TMDB to raw.substringAfter("tmdb://")
                    raw.startsWith("tvdb://", ignoreCase = true) -> Provider.TVDB to raw.substringAfter("tvdb://")
                    raw.startsWith("imdb://", ignoreCase = true) -> Provider.IMDB to raw.substringAfter("imdb://")
                    else -> null
                }
            }.map { (provider, externalId) -> provider to externalId.trim() }
            .filter { (_, externalId) -> externalId.isNotBlank() }
            .distinct()

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

    private fun extractShowExternalIds(guids: List<PlexGuid>): List<Pair<Provider, String>> =
        guids
            .asSequence()
            .mapNotNull { guid ->
                val raw = guid.id.trim()
                when {
                    raw.startsWith("tmdb://", ignoreCase = true) -> Provider.TMDB to raw.substringAfter("tmdb://")
                    raw.startsWith("imdb://", ignoreCase = true) -> Provider.IMDB to raw.substringAfter("imdb://")
                    raw.startsWith("tvdb://", ignoreCase = true) -> Provider.TVDB to raw.substringAfter("tvdb://")
                    else -> null
                }
            }.map { (provider, externalId) -> provider to externalId.trim() }
            .filter { (_, externalId) -> externalId.isNotBlank() }
            .distinct()
            .toList()

    private fun resolveSlug(slug: String?): String? = slug?.trim()?.ifBlank { null }
}
