package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.controller.webhook.dto.PlexWebhookPayload
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.model.movie.MovieTitleSource
import dev.marcal.mediapulse.server.model.movie.MovieWatch
import dev.marcal.mediapulse.server.model.movie.MovieWatchSource
import dev.marcal.mediapulse.server.model.plex.PlexEventType
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.repository.crud.MovieTitleCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieWatchCrudRepository
import dev.marcal.mediapulse.server.util.FingerprintUtil
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PlexMovieWatchService(
    private val movieRepository: MovieRepository,
    private val movieTitleCrudRepository: MovieTitleCrudRepository,
    private val movieWatchCrudRepository: MovieWatchCrudRepository,
    private val externalIdentifierRepository: ExternalIdentifierRepository,
    private val plexMovieArtworkService: PlexMovieArtworkService,
) {
    @Transactional
    suspend fun processScrobble(payload: PlexWebhookPayload): MovieWatch? {
        val meta = payload.metadata
        if (meta.type != "movie") return null

        val eventType = requireNotNull(PlexEventType.fromType(payload.event)) { "event type is not supported: ${payload.event}" }
        if (eventType != PlexEventType.SCROBBLE) return null

        val originalTitle = meta.originalTitle?.trim()?.ifBlank { null } ?: meta.title
        val localizedTitle = meta.title.trim()
        val description = meta.summary?.trim()?.ifBlank { null }
        val year = meta.year
        val slug = resolveSlug(meta.slug)

        val fingerprint = FingerprintUtil.movieFp(originalTitle = originalTitle, year = year)
        val existing = movieRepository.findByFingerprint(fingerprint)

        val movie =
            if (existing == null) {
                movieRepository.save(
                    Movie(
                        originalTitle = originalTitle,
                        year = year,
                        description = description,
                        slug = slug,
                        fingerprint = fingerprint,
                    ),
                )
            } else {
                val merged = mergeMovie(existing = existing, incomingYear = year, incomingDescription = description, incomingSlug = slug)
                if (merged != existing) movieRepository.save(merged) else existing
            }

        movieTitleCrudRepository.insertIgnore(
            movieId = movie.id,
            title = movie.originalTitle,
            locale = null,
            source = MovieTitleSource.PLEX.name,
            isPrimary = true,
        )

        if (localizedTitle != movie.originalTitle) {
            movieTitleCrudRepository.insertIgnore(
                movieId = movie.id,
                title = localizedTitle,
                locale = null,
                source = MovieTitleSource.PLEX.name,
                isPrimary = false,
            )
        }

        persistExternalIds(movie.id, meta.guidList)
        plexMovieArtworkService.ensureMovieImagesFromPlex(
            movie = movie,
            images =
                meta.image.map { img ->
                    PlexMovieArtworkService.PlexMovieImageCandidate(
                        url = img.url,
                        isPoster = img.type.equals("coverPoster", ignoreCase = true),
                    )
                },
            fallbackThumbPath = meta.thumb,
        )

        val watchedAt = meta.lastViewedAt ?: Instant.now()

        movieWatchCrudRepository.insertIgnore(
            movieId = movie.id,
            source = MovieWatchSource.PLEX.name,
            watchedAt = watchedAt,
        )

        return MovieWatch(movieId = movie.id, source = MovieWatchSource.PLEX, watchedAt = watchedAt)
    }

    private fun mergeMovie(
        existing: Movie,
        incomingYear: Int?,
        incomingDescription: String?,
        incomingSlug: String?,
    ): Movie {
        val updatedYear = existing.year ?: incomingYear
        val updatedDescription = incomingDescription ?: existing.description
        val updatedSlug = incomingSlug ?: existing.slug

        val changed =
            updatedYear != existing.year ||
                updatedDescription != existing.description ||
                updatedSlug != existing.slug

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

    private fun resolveSlug(slug: String?): String? = slug?.trim()?.ifBlank { null }

    private fun persistExternalIds(
        movieId: Long,
        guidList: List<PlexWebhookPayload.PlexMetadata.PlexGuidMetadata>,
    ) {
        guidList
            .asSequence()
            .mapNotNull { guid ->
                val raw = guid.id?.trim()?.ifBlank { null } ?: return@mapNotNull null
                when {
                    raw.startsWith("tmdb://", ignoreCase = true) -> Provider.TMDB to raw.substringAfter("tmdb://")
                    raw.startsWith("imdb://", ignoreCase = true) -> Provider.IMDB to raw.substringAfter("imdb://")
                    else -> null
                }
            }.map { (provider, externalId) -> provider to externalId.trim() }
            .filter { (_, externalId) -> externalId.isNotBlank() }
            .distinct()
            .forEach { (provider, externalId) ->
                safeLink(movieId = movieId, provider = provider, externalId = externalId)
            }
    }

    private fun safeLink(
        movieId: Long,
        provider: Provider,
        externalId: String,
    ) {
        if (externalIdentifierRepository.findByProviderAndExternalId(provider, externalId) != null) return

        externalIdentifierRepository.save(
            ExternalIdentifier(
                entityType = EntityType.MOVIE,
                entityId = movieId,
                provider = provider,
                externalId = externalId,
            ),
        )
    }
}
