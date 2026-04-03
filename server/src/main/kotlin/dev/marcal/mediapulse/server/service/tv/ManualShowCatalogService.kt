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
import org.springframework.stereotype.Service
import java.time.Instant

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
        val episode: TvEpisode,
        val createdShow: Boolean,
        val createdEpisode: Boolean,
        val coverAssigned: Boolean,
    )

    fun resolveOrCreate(request: ManualShowWatchCreateRequest): ShowCatalogResult {
        val normalizedShowTitle = request.showTitle.trim().ifBlank { throw IllegalArgumentException("showTitle deve ser preenchido") }
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

        val existingByTmdb = normalizedTmdbId?.let { findShowByExternalId(Provider.TMDB, it) }
        val existingByTvdb = if (existingByTmdb == null) normalizedTvdbId?.let { findShowByExternalId(Provider.TVDB, it) } else null

        val fingerprint = FingerprintUtil.tvShowFp(originalTitle = resolvedOriginalTitle, year = resolvedYear)
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
                        originalTitle = resolvedOriginalTitle,
                        year = resolvedYear,
                        description = resolvedDescription,
                        slug = resolvedSlug,
                        fingerprint = fingerprint,
                    ),
                )

        val showAfterMetadata = fillMissingShowMetadata(show, resolvedYear, resolvedDescription, resolvedSlug)

        tvShowTitleCrudRepository.insertIgnore(
            showId = showAfterMetadata.id,
            title = normalizedShowTitle,
            locale = null,
            source = TvShowTitleSource.MANUAL.name,
            isPrimary = true,
        )
        tmdbDetails
            ?.title
            ?.takeIf { it.lowercase() != normalizedShowTitle.lowercase() }
            ?.let { tmdbTitle ->
                tvShowTitleCrudRepository.insertIgnore(
                    showId = showAfterMetadata.id,
                    title = tmdbTitle,
                    locale = null,
                    source = TvShowTitleSource.MANUAL.name,
                    isPrimary = false,
                )
            }

        normalizedTmdbId?.let { safeLink(EntityType.SHOW, showAfterMetadata.id, Provider.TMDB, it) }
        normalizedTvdbId?.let { safeLink(EntityType.SHOW, showAfterMetadata.id, Provider.TVDB, it) }

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

        return ShowCatalogResult(
            show = refreshedShow,
            episode = episodeAfterMetadata,
            createdShow = createdShow,
            createdEpisode = createdEpisode,
            coverAssigned = coverAssigned,
        )
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
        if (externalIdentifierRepository.findByProviderAndExternalId(provider, externalId) != null) return

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

    private fun buildTmdbImageUrl(path: String): String {
        val normalizedPath = if (path.startsWith('/')) path else "/$path"
        val normalizedImageBaseUrl = tmdbProperties.imageBaseUrl.trimEnd('/')
        return "$normalizedImageBaseUrl/t/p/w780$normalizedPath"
    }

    private fun resolveSlug(title: String): String? {
        val normalized = SlugTextUtil.normalize(title).replace('_', '-')
        return normalized.ifBlank { null }
    }
}
