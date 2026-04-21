package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ManualShowWatchCreateRequest
import dev.marcal.mediapulse.server.config.TmdbProperties
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.integration.tmdb.TmdbImageClient
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.tv.TvEpisode
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.model.tv.TvShowTitleSource
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowTitleCrudRepository
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import dev.marcal.mediapulse.server.util.FingerprintUtil
import dev.marcal.mediapulse.server.util.SlugTextUtil
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate

@Service
class ManualShowCatalogService(
    private val tvShowRepository: TvShowRepository,
    private val tvShowTitleCrudRepository: TvShowTitleCrudRepository,
    private val tvEpisodeRepository: TvEpisodeRepository,
    private val tvShowImageCrudRepository: TvShowImageCrudRepository,
    private val externalIdentifierRepository: ExternalIdentifierRepository,
    private val tmdbApiClient: TmdbApiClient,
    private val tmdbImageClient: TmdbImageClient,
    private val imageStorageService: ImageStorageService,
    private val tmdbProperties: TmdbProperties,
) {
    data class ShowCatalogResult(
        val show: TvShow,
        val createdShow: Boolean,
        val coverAssigned: Boolean,
        val seasonsImported: Int = 0,
        val episodesImported: Int = 0,
    )

    data class ShowWatchCatalogResult(
        val show: TvShow,
        val episode: TvEpisode,
        val createdShow: Boolean,
        val createdEpisode: Boolean,
        val coverAssigned: Boolean,
    )

    data class ShowCatalogUpsertRequest(
        val title: String,
        val year: Int? = null,
        val tmdbId: String? = null,
        val tvdbId: String? = null,
        val importEpisodes: Boolean = true,
    )

    fun resolveOrCreateCatalog(request: ShowCatalogUpsertRequest): ShowCatalogResult {
        val normalizedShowTitle = request.title.trim().ifBlank { throw IllegalArgumentException("title deve ser preenchido") }
        val normalizedTmdbId = request.tmdbId?.trim()?.ifBlank { null }
        val normalizedTvdbId = request.tvdbId?.trim()?.ifBlank { null }
        val tmdbDetails = normalizedTmdbId?.let { tmdbApiClient.fetchShowDetails(it) }
        val resolvedOriginalTitle = tmdbDetails?.originalTitle ?: normalizedShowTitle
        val resolvedYear = request.year ?: tmdbDetails?.firstAirYear
        val resolvedDescription = tmdbDetails?.overview
        val resolvedSlug = resolveSlug(tmdbDetails?.title ?: resolvedOriginalTitle)

        val showResult =
            resolveOrCreateShow(
                normalizedShowTitle = normalizedShowTitle,
                originalTitle = resolvedOriginalTitle,
                year = resolvedYear,
                description = resolvedDescription,
                slug = resolvedSlug,
                tmdbId = normalizedTmdbId,
                tvdbId = normalizedTvdbId,
                tmdbTitle = tmdbDetails?.title,
            )

        val coverAssigned = maybeAssignTmdbImages(showResult.show, tmdbDetails)
        val importResult =
            if (request.importEpisodes && normalizedTmdbId != null && tmdbDetails != null) {
                importTmdbEpisodes(showResult.show.id, normalizedTmdbId, tmdbDetails)
            } else {
                EpisodeImportResult()
            }
        val refreshedShow = tvShowRepository.findById(showResult.show.id).orElse(showResult.show)

        return ShowCatalogResult(
            show = refreshedShow,
            createdShow = showResult.createdShow,
            coverAssigned = coverAssigned,
            seasonsImported = importResult.seasonsImported,
            episodesImported = importResult.episodesImported,
        )
    }

    fun resolveOrCreate(request: ManualShowWatchCreateRequest): ShowWatchCatalogResult {
        val normalizedShowTitle =
            request.showTitle.trim().ifBlank { throw IllegalArgumentException("showTitle deve ser preenchido") }
        val normalizedEpisodeTitle =
            request.episodeTitle.trim().ifBlank {
                throw IllegalArgumentException(
                    "episodeTitle deve ser preenchido",
                )
            }
        val normalizedTmdbId = request.tmdbId?.trim()?.ifBlank { null }
        val normalizedTvdbId = request.tvdbId?.trim()?.ifBlank { null }
        val tmdbDetails = normalizedTmdbId?.let { tmdbApiClient.fetchShowDetails(it) }
        val resolvedOriginalTitle = tmdbDetails?.originalTitle ?: normalizedShowTitle
        val resolvedYear = request.year ?: tmdbDetails?.firstAirYear
        val resolvedDescription = tmdbDetails?.overview
        val resolvedSlug = resolveSlug(tmdbDetails?.title ?: resolvedOriginalTitle)

        val showResult =
            resolveOrCreateShow(
                normalizedShowTitle = normalizedShowTitle,
                originalTitle = resolvedOriginalTitle,
                year = resolvedYear,
                description = resolvedDescription,
                slug = resolvedSlug,
                tmdbId = normalizedTmdbId,
                tvdbId = normalizedTvdbId,
                tmdbTitle = tmdbDetails?.title,
            )
        val showAfterMetadata = showResult.show

        val episodeFingerprint =
            FingerprintUtil.tvEpisodeFp(
                showId = showAfterMetadata.id,
                seasonNumber = request.seasonNumber,
                episodeNumber = request.episodeNumber,
                title = normalizedEpisodeTitle,
            )

        val existingEpisode =
            tvEpisodeRepository.findByFingerprint(episodeFingerprint)
                ?: tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(
                    showAfterMetadata.id,
                    request.seasonNumber,
                    request.episodeNumber,
                )

        val createdEpisode = existingEpisode == null
        val episode =
            existingEpisode
                ?: tvEpisodeRepository.save(
                    TvEpisode(
                        showId = showAfterMetadata.id,
                        title = normalizedEpisodeTitle,
                        seasonNumber = request.seasonNumber,
                        episodeNumber = request.episodeNumber,
                        originallyAvailableAt = request.originallyAvailableAt,
                        fingerprint = episodeFingerprint,
                    ),
                )

        val episodeAfterMetadata = fillMissingEpisodeMetadata(episode, normalizedEpisodeTitle, request)
        val coverAssigned = maybeAssignTmdbImages(showAfterMetadata, tmdbDetails)
        val refreshedShow = tvShowRepository.findById(showAfterMetadata.id).orElse(showAfterMetadata)

        return ShowWatchCatalogResult(
            show = refreshedShow,
            episode = episodeAfterMetadata,
            createdShow = showResult.createdShow,
            createdEpisode = createdEpisode,
            coverAssigned = coverAssigned,
        )
    }

    fun buildTmdbImageUrl(path: String): String {
        val normalizedPath = if (path.startsWith('/')) path else "/$path"
        val normalizedImageBaseUrl = tmdbProperties.imageBaseUrl.trimEnd('/')
        return "$normalizedImageBaseUrl/t/p/w780$normalizedPath"
    }

    private data class ShowUpsertResult(
        val show: TvShow,
        val createdShow: Boolean,
    )

    private data class EpisodeImportResult(
        val seasonsImported: Int = 0,
        val episodesImported: Int = 0,
    )

    private fun resolveOrCreateShow(
        normalizedShowTitle: String,
        originalTitle: String,
        year: Int?,
        description: String?,
        slug: String?,
        tmdbId: String?,
        tvdbId: String?,
        tmdbTitle: String?,
    ): ShowUpsertResult {
        val existingByTmdb = tmdbId?.let { findShowByExternalId(Provider.TMDB, it) }
        val existingByTvdb = if (existingByTmdb == null) tvdbId?.let { findShowByExternalId(Provider.TVDB, it) } else null

        val fingerprint = FingerprintUtil.tvShowFp(originalTitle = originalTitle, year = year)
        val existingByFingerprint =
            if (existingByTmdb == null && existingByTvdb == null) {
                tvShowRepository.findByFingerprint(fingerprint)
            } else {
                null
            }

        val existingShow = existingByTmdb ?: existingByTvdb ?: existingByFingerprint
        val createdShow = existingShow == null

        val show =
            existingShow
                ?: tvShowRepository.save(
                    TvShow(
                        originalTitle = originalTitle,
                        year = year,
                        description = description,
                        slug = slug,
                        fingerprint = fingerprint,
                    ),
                )

        val showAfterMetadata = fillMissingShowMetadata(show, year, description, slug)

        tvShowTitleCrudRepository.insertIgnore(
            showId = showAfterMetadata.id,
            title = normalizedShowTitle,
            locale = null,
            source = TvShowTitleSource.MANUAL.name,
            isPrimary = true,
        )
        tmdbTitle
            ?.takeIf { it.lowercase() != normalizedShowTitle.lowercase() }
            ?.let { title ->
                tvShowTitleCrudRepository.insertIgnore(
                    showId = showAfterMetadata.id,
                    title = title,
                    locale = null,
                    source = TvShowTitleSource.MANUAL.name,
                    isPrimary = false,
                )
            }

        tmdbId?.let { safeLink(EntityType.SHOW, showAfterMetadata.id, Provider.TMDB, it) }
        tvdbId?.let { safeLink(EntityType.SHOW, showAfterMetadata.id, Provider.TVDB, it) }

        return ShowUpsertResult(show = showAfterMetadata, createdShow = createdShow)
    }

    private fun importTmdbEpisodes(
        showId: Long,
        tmdbId: String,
        tmdbDetails: TmdbApiClient.TmdbShowDetails,
    ): EpisodeImportResult {
        var seasonsImported = 0
        var episodesImported = 0

        tmdbDetails.seasons
            .asSequence()
            .mapNotNull { it.seasonNumber }
            .filter { it > 0 }
            .distinct()
            .sorted()
            .forEach { seasonNumber ->
                val seasonDetails = tmdbApiClient.fetchShowSeasonDetails(tmdbId, seasonNumber) ?: return@forEach
                var seasonChanged = false
                val seasonTitle = seasonDetails.title?.trim()?.ifBlank { null }

                seasonDetails.episodes.forEach episodeLoop@{ tmdbEpisode ->
                    val episodeNumber = tmdbEpisode.episodeNumber ?: return@episodeLoop
                    val title = tmdbEpisode.title ?: "Episódio $episodeNumber"
                    val fingerprint =
                        FingerprintUtil.tvEpisodeFp(
                            showId = showId,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            title = title,
                        )
                    val existing =
                        tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(showId, seasonNumber, episodeNumber)
                            ?: tvEpisodeRepository.findByFingerprint(fingerprint)

                    val airDate = parseLocalDate(tmdbEpisode.airDate)
                    val durationMs = tmdbEpisode.runtimeMinutes?.let { it * 60 * 1000 }

                    val episode =
                        if (existing == null) {
                            tvEpisodeRepository.save(
                                TvEpisode(
                                    showId = showId,
                                    title = title,
                                    seasonNumber = seasonNumber,
                                    seasonTitle = seasonTitle,
                                    episodeNumber = episodeNumber,
                                    summary = tmdbEpisode.overview,
                                    durationMs = durationMs,
                                    originallyAvailableAt = airDate,
                                    fingerprint = fingerprint,
                                ),
                            )
                        } else {
                            fillMissingImportedEpisodeMetadata(
                                episode = existing,
                                title = title,
                                seasonTitle = seasonTitle,
                                summary = tmdbEpisode.overview,
                                durationMs = durationMs,
                                originallyAvailableAt = airDate,
                            )
                        }

                    tmdbEpisode.tmdbId?.let { safeLink(EntityType.EPISODE, episode.id, Provider.TMDB, it) }
                    episodesImported += 1
                    seasonChanged = true
                }

                if (seasonChanged) {
                    seasonsImported += 1
                }
            }

        return EpisodeImportResult(seasonsImported = seasonsImported, episodesImported = episodesImported)
    }

    private fun findShowByExternalId(
        provider: Provider,
        externalId: String,
    ): TvShow? {
        val externalIdentifier =
            externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(
                entityType = EntityType.SHOW,
                provider = provider,
                externalId = externalId,
            ) ?: return null

        return tvShowRepository.findById(externalIdentifier.entityId).orElse(null)
    }

    private fun safeLink(
        entityType: EntityType,
        entityId: Long,
        provider: Provider,
        externalId: String,
    ) {
        val existing = externalIdentifierRepository.findByProviderAndExternalId(provider, externalId)
        if (existing != null) {
            if (existing.entityType == entityType && existing.entityId == entityId) {
                return
            }

            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "${provider.name} $externalId já está vinculado a outra entidade",
            )
        }

        externalIdentifierRepository.save(
            ExternalIdentifier(
                entityType = entityType,
                entityId = entityId,
                provider = provider,
                externalId = externalId,
            ),
        )
    }

    private fun fillMissingShowMetadata(
        show: TvShow,
        year: Int?,
        description: String?,
        slug: String?,
    ): TvShow {
        val updatedYear = show.year ?: year
        val updatedDescription = show.description ?: description
        val updatedSlug = show.slug ?: slug

        val changed =
            updatedYear != show.year ||
                updatedDescription != show.description ||
                updatedSlug != show.slug

        if (!changed) return show

        return tvShowRepository.save(
            show.copy(
                year = updatedYear,
                description = updatedDescription,
                slug = updatedSlug,
                updatedAt = Instant.now(),
            ),
        )
    }

    private fun fillMissingEpisodeMetadata(
        episode: TvEpisode,
        title: String,
        request: ManualShowWatchCreateRequest,
    ): TvEpisode {
        val updatedTitle = if (episode.title.isBlank()) title else episode.title
        val updatedSeasonNumber = episode.seasonNumber ?: request.seasonNumber
        val updatedEpisodeNumber = episode.episodeNumber ?: request.episodeNumber
        val updatedOriginallyAvailableAt = episode.originallyAvailableAt ?: request.originallyAvailableAt

        val changed =
            updatedTitle != episode.title ||
                updatedSeasonNumber != episode.seasonNumber ||
                updatedEpisodeNumber != episode.episodeNumber ||
                updatedOriginallyAvailableAt != episode.originallyAvailableAt

        if (!changed) return episode

        return tvEpisodeRepository.save(
            episode.copy(
                title = updatedTitle,
                seasonNumber = updatedSeasonNumber,
                episodeNumber = updatedEpisodeNumber,
                originallyAvailableAt = updatedOriginallyAvailableAt,
                updatedAt = Instant.now(),
            ),
        )
    }

    private fun fillMissingImportedEpisodeMetadata(
        episode: TvEpisode,
        title: String,
        seasonTitle: String?,
        summary: String?,
        durationMs: Int?,
        originallyAvailableAt: LocalDate?,
    ): TvEpisode {
        val updatedTitle =
            if (episode.title.isBlank() ||
                isGenericEpisodeTitle(episode.title, episode.episodeNumber)
            ) {
                title
            } else {
                episode.title
            }
        val updatedSeasonTitle = episode.seasonTitle ?: seasonTitle
        val updatedSummary = episode.summary ?: summary
        val updatedDurationMs = episode.durationMs ?: durationMs
        val updatedOriginallyAvailableAt = episode.originallyAvailableAt ?: originallyAvailableAt

        val changed =
            updatedTitle != episode.title ||
                updatedSeasonTitle != episode.seasonTitle ||
                updatedSummary != episode.summary ||
                updatedDurationMs != episode.durationMs ||
                updatedOriginallyAvailableAt != episode.originallyAvailableAt

        if (!changed) return episode

        return tvEpisodeRepository.save(
            episode.copy(
                title = updatedTitle,
                seasonTitle = updatedSeasonTitle,
                summary = updatedSummary,
                durationMs = updatedDurationMs,
                originallyAvailableAt = updatedOriginallyAvailableAt,
                updatedAt = Instant.now(),
            ),
        )
    }

    private fun maybeAssignTmdbImages(
        show: TvShow,
        tmdbDetails: TmdbApiClient.TmdbShowDetails?,
    ): Boolean {
        if (tmdbDetails == null) return false

        val candidates =
            buildList {
                tmdbDetails.posterPath?.let { add(it to true) }
                tmdbDetails.backdropPath?.let { add(it to false) }
            }.distinctBy { it.first }

        if (candidates.isEmpty()) return false

        val savedImages = mutableListOf<Pair<String, Boolean>>()

        candidates.forEach { (path, preferredPrimary) ->
            val localPath =
                runCatching {
                    val fullUrl = buildTmdbImageUrl(path)
                    val image = tmdbImageClient.downloadImage(fullUrl)
                    val fileHint = "${show.originalTitle}_${DigestUtils.sha1Hex(fullUrl).take(12)}"
                    imageStorageService.saveImageForTvShow(
                        image = image,
                        provider = "TMDB",
                        showId = show.id,
                        fileNameHint = fileHint,
                    )
                }.getOrNull() ?: return@forEach
            savedImages += localPath to preferredPrimary
        }

        if (savedImages.isEmpty()) return false

        val hasPrimaryImage = tvShowImageCrudRepository.existsByShowIdAndIsPrimaryTrue(show.id)
        if (hasPrimaryImage) {
            savedImages.forEach { (localPath, _) ->
                tvShowImageCrudRepository.insertIgnore(show.id, localPath, false)
            }
            return false
        }

        val primaryLocalPath = savedImages.firstOrNull { it.second }?.first ?: savedImages.first().first

        savedImages.forEach { (localPath, _) ->
            tvShowImageCrudRepository.insertIgnore(
                showId = show.id,
                url = localPath,
                isPrimary = localPath == primaryLocalPath,
            )
        }

        if (show.coverUrl == null) {
            tvShowRepository.save(show.copy(coverUrl = primaryLocalPath, updatedAt = Instant.now()))
        }

        return true
    }

    private fun resolveSlug(title: String): String? {
        val normalized = SlugTextUtil.normalize(title).replace('_', '-')
        return normalized.ifBlank { null }
    }

    private fun parseLocalDate(value: String?): LocalDate? =
        value
            ?.trim()
            ?.ifBlank { null }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun isGenericEpisodeTitle(
        title: String,
        episodeNumber: Int?,
    ): Boolean {
        if (episodeNumber == null) return false
        val escapedNumber = Regex.escape(episodeNumber.toString())
        val pattern = Regex("""(?i)^(episode|epis[oó]dio|ep\.?)\s*0*$escapedNumber$""")
        return pattern.matches(title.trim())
    }
}
