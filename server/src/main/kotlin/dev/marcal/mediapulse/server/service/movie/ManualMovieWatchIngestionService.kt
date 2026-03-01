package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.ManualMovieWatchIngestItemRequest
import dev.marcal.mediapulse.server.api.movies.ManualMovieWatchIngestItemResult
import dev.marcal.mediapulse.server.api.movies.ManualMovieWatchIngestRequest
import dev.marcal.mediapulse.server.api.movies.ManualMovieWatchIngestResponse
import dev.marcal.mediapulse.server.config.TmdbProperties
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.model.movie.MovieTitleSource
import dev.marcal.mediapulse.server.model.movie.MovieWatchSource
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.MovieImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.repository.crud.MovieTitleCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieWatchCrudRepository
import dev.marcal.mediapulse.server.util.FingerprintUtil
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ManualMovieWatchIngestionService(
    private val movieRepository: MovieRepository,
    private val movieTitleCrudRepository: MovieTitleCrudRepository,
    private val movieWatchCrudRepository: MovieWatchCrudRepository,
    private val movieImageCrudRepository: MovieImageCrudRepository,
    private val externalIdentifierRepository: ExternalIdentifierRepository,
    private val tmdbApiClient: TmdbApiClient,
    private val tmdbProperties: TmdbProperties,
) {
    fun ingest(request: ManualMovieWatchIngestRequest): ManualMovieWatchIngestResponse {
        val results = request.items.map { processItem(it) }
        return ManualMovieWatchIngestResponse(items = results)
    }

    @Transactional
    fun processItem(item: ManualMovieWatchIngestItemRequest): ManualMovieWatchIngestItemResult {
        val normalizedTitle = item.title.trim().ifBlank { throw IllegalArgumentException("title deve ser preenchido") }
        val normalizedTmdbId = item.tmdbId?.trim()?.ifBlank { null }
        val normalizedImdbId = item.imdbId?.trim()?.ifBlank { null }

        val existingByTmdb = normalizedTmdbId?.let { findMovieByExternalId(Provider.TMDB, it) }
        val existingByImdb = if (existingByTmdb == null) normalizedImdbId?.let { findMovieByExternalId(Provider.IMDB, it) } else null

        val fingerprint = FingerprintUtil.movieFp(originalTitle = normalizedTitle, year = item.year)
        val existingByFingerprint =
            if (existingByTmdb == null &&
                existingByImdb == null
            ) {
                movieRepository.findByFingerprint(fingerprint)
            } else {
                null
            }

        val existingMovie = existingByTmdb ?: existingByImdb ?: existingByFingerprint
        val created = existingMovie == null

        val movie =
            existingMovie
                ?: movieRepository.save(
                    Movie(
                        originalTitle = normalizedTitle,
                        year = item.year,
                        fingerprint = fingerprint,
                    ),
                )

        movieTitleCrudRepository.insertIgnore(
            movieId = movie.id,
            title = normalizedTitle,
            locale = null,
            source = MovieTitleSource.MANUAL.name,
            isPrimary = true,
        )

        normalizedTmdbId?.let { safeLink(movie.id, Provider.TMDB, it) }
        normalizedImdbId?.let { safeLink(movie.id, Provider.IMDB, it) }

        val watchAlreadyExists =
            movieWatchCrudRepository.existsByMovieIdAndSourceAndWatchedAt(
                movieId = movie.id,
                source = MovieWatchSource.MANUAL,
                watchedAt = item.watchedAt,
            )

        movieWatchCrudRepository.insertIgnore(
            movieId = movie.id,
            source = MovieWatchSource.MANUAL.name,
            watchedAt = item.watchedAt,
        )

        val coverAssigned = maybeAssignTmdbCover(movie = movie, tmdbId = normalizedTmdbId)

        return ManualMovieWatchIngestItemResult(
            movieId = movie.id,
            created = created,
            watchInserted = !watchAlreadyExists,
            coverAssigned = coverAssigned,
        )
    }

    private fun findMovieByExternalId(
        provider: Provider,
        externalId: String,
    ): Movie? {
        val externalIdentifier =
            externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(
                entityType = EntityType.MOVIE,
                provider = provider,
                externalId = externalId,
            ) ?: return null

        return movieRepository.findById(externalIdentifier.entityId).orElse(null)
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

    private fun maybeAssignTmdbCover(
        movie: Movie,
        tmdbId: String?,
    ): Boolean {
        if (tmdbId == null) return false

        val hasPrimaryImage = movieImageCrudRepository.existsByMovieIdAndIsPrimaryTrue(movie.id)
        if (hasPrimaryImage) return false

        val posterPath = tmdbApiClient.fetchPosterPath(tmdbId) ?: return false
        val fullPosterUrl = buildTmdbImageUrl(posterPath)

        movieImageCrudRepository.insertIgnore(
            movieId = movie.id,
            url = fullPosterUrl,
            isPrimary = true,
        )

        if (movie.coverUrl == null) {
            movieRepository.save(
                movie.copy(
                    coverUrl = fullPosterUrl,
                    updatedAt = Instant.now(),
                ),
            )
        }

        return true
    }

    private fun buildTmdbImageUrl(posterPath: String): String {
        val normalizedPath = if (posterPath.startsWith('/')) posterPath else "/$posterPath"
        val normalizedImageBaseUrl = tmdbProperties.imageBaseUrl.trimEnd('/')
        return "$normalizedImageBaseUrl/t/p/w780$normalizedPath"
    }
}
