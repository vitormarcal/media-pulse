package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.ManualMovieWatchCreateRequest
import dev.marcal.mediapulse.server.config.TmdbProperties
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.integration.tmdb.TmdbImageClient
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.model.movie.MovieTitleSource
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.MovieImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.repository.crud.MovieTitleCrudRepository
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import dev.marcal.mediapulse.server.util.FingerprintUtil
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ManualMovieCatalogService(
    private val movieRepository: MovieRepository,
    private val movieTitleCrudRepository: MovieTitleCrudRepository,
    private val movieImageCrudRepository: MovieImageCrudRepository,
    private val externalIdentifierRepository: ExternalIdentifierRepository,
    private val tmdbApiClient: TmdbApiClient,
    private val tmdbImageClient: TmdbImageClient,
    private val imageStorageService: ImageStorageService,
    private val tmdbProperties: TmdbProperties,
) {
    data class MovieCatalogResult(
        val movie: Movie,
        val created: Boolean,
        val coverAssigned: Boolean,
    )

    fun resolveOrCreate(request: ManualMovieWatchCreateRequest): MovieCatalogResult {
        val normalizedTitle = request.title.trim().ifBlank { throw IllegalArgumentException("title deve ser preenchido") }
        val normalizedTmdbId = request.tmdbId?.trim()?.ifBlank { null }
        val normalizedImdbId = request.imdbId?.trim()?.ifBlank { null }

        val existingByTmdb = normalizedTmdbId?.let { findMovieByExternalId(Provider.TMDB, it) }
        val existingByImdb = if (existingByTmdb == null) normalizedImdbId?.let { findMovieByExternalId(Provider.IMDB, it) } else null

        val fingerprint = FingerprintUtil.movieFp(originalTitle = normalizedTitle, year = request.year)
        val existingByFingerprint =
            if (existingByTmdb == null && existingByImdb == null) {
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
                        year = request.year,
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

        val coverAssigned = maybeAssignTmdbCover(movie = movie, tmdbId = normalizedTmdbId)
        val refreshedMovie = movieRepository.findById(movie.id).orElse(movie)

        return MovieCatalogResult(
            movie = refreshedMovie,
            created = created,
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
        val localPosterPath =
            runCatching {
                val image = tmdbImageClient.downloadImage(fullPosterUrl)
                val fileHint = "${movie.originalTitle}_${DigestUtils.sha1Hex(fullPosterUrl).take(12)}"
                imageStorageService.saveImageForMovie(
                    image = image,
                    provider = "TMDB",
                    movieId = movie.id,
                    fileNameHint = fileHint,
                )
            }.getOrNull() ?: return false

        movieImageCrudRepository.insertIgnore(
            movieId = movie.id,
            url = localPosterPath,
            isPrimary = true,
        )

        if (movie.coverUrl == null) {
            movieRepository.save(
                movie.copy(
                    coverUrl = localPosterPath,
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
