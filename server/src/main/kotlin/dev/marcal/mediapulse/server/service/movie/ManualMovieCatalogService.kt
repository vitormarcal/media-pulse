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
import dev.marcal.mediapulse.server.util.SlugTextUtil
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
        val tmdbDetails = normalizedTmdbId?.let { tmdbApiClient.fetchMovieDetails(it) }
        val resolvedOriginalTitle = tmdbDetails?.originalTitle ?: normalizedTitle
        val resolvedYear = request.year ?: tmdbDetails?.releaseYear
        val resolvedDescription = tmdbDetails?.overview
        val resolvedSlug = resolveSlug(tmdbDetails?.title ?: resolvedOriginalTitle)

        val existingByTmdb = normalizedTmdbId?.let { findMovieByExternalId(Provider.TMDB, it) }
        val existingByImdb = if (existingByTmdb == null) normalizedImdbId?.let { findMovieByExternalId(Provider.IMDB, it) } else null

        val fingerprint = FingerprintUtil.movieFp(originalTitle = resolvedOriginalTitle, year = resolvedYear)
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
                        originalTitle = resolvedOriginalTitle,
                        year = resolvedYear,
                        description = resolvedDescription,
                        slug = resolvedSlug,
                        fingerprint = fingerprint,
                    ),
                )

        val movieAfterMetadata = fillMissingMovieMetadata(movie, resolvedYear, resolvedDescription, resolvedSlug)

        movieTitleCrudRepository.insertIgnore(
            movieId = movieAfterMetadata.id,
            title = normalizedTitle,
            locale = null,
            source = MovieTitleSource.MANUAL.name,
            isPrimary = true,
        )
        tmdbDetails
            ?.title
            ?.takeIf { it.lowercase() != normalizedTitle.lowercase() }
            ?.let { tmdbTitle ->
                movieTitleCrudRepository.insertIgnore(
                    movieId = movieAfterMetadata.id,
                    title = tmdbTitle,
                    locale = null,
                    source = MovieTitleSource.MANUAL.name,
                    isPrimary = false,
                )
            }

        normalizedTmdbId?.let { safeLink(movieAfterMetadata.id, Provider.TMDB, it) }
        normalizedImdbId?.let { safeLink(movieAfterMetadata.id, Provider.IMDB, it) }

        val coverAssigned = maybeAssignTmdbImages(movie = movieAfterMetadata, tmdbDetails = tmdbDetails)
        val refreshedMovie = movieRepository.findById(movieAfterMetadata.id).orElse(movieAfterMetadata)

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

    private fun fillMissingMovieMetadata(
        movie: Movie,
        year: Int?,
        description: String?,
        slug: String?,
    ): Movie {
        val updatedYear = movie.year ?: year
        val updatedDescription = movie.description ?: description
        val updatedSlug = movie.slug ?: slug

        val changed =
            updatedYear != movie.year ||
                updatedDescription != movie.description ||
                updatedSlug != movie.slug

        if (!changed) return movie

        return movieRepository.save(
            movie.copy(
                year = updatedYear,
                description = updatedDescription,
                slug = updatedSlug,
                updatedAt = Instant.now(),
            ),
        )
    }

    private fun maybeAssignTmdbImages(
        movie: Movie,
        tmdbDetails: TmdbApiClient.TmdbMovieDetails?,
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
                    val fileHint = "${movie.originalTitle}_${DigestUtils.sha1Hex(fullUrl).take(12)}"
                    imageStorageService.saveImageForMovie(
                        image = image,
                        provider = "TMDB",
                        movieId = movie.id,
                        fileNameHint = fileHint,
                    )
                }.getOrNull() ?: return@forEach
            savedImages += localPath to preferredPrimary
        }

        if (savedImages.isEmpty()) return false

        val hasPrimaryImage = movieImageCrudRepository.existsByMovieIdAndIsPrimaryTrue(movie.id)
        if (hasPrimaryImage) {
            savedImages.forEach { (localPath, _) ->
                movieImageCrudRepository.insertIgnore(
                    movieId = movie.id,
                    url = localPath,
                    isPrimary = false,
                )
            }
            return false
        }

        val primaryLocalPath = savedImages.firstOrNull { it.second }?.first ?: savedImages.first().first

        savedImages.forEach { (localPath, _) ->
            movieImageCrudRepository.insertIgnore(
                movieId = movie.id,
                url = localPath,
                isPrimary = localPath == primaryLocalPath,
            )
        }

        if (movie.coverUrl == null) {
            movieRepository.save(movie.copy(coverUrl = primaryLocalPath, updatedAt = Instant.now()))
        }

        return true
    }

    private fun buildTmdbImageUrl(posterPath: String): String {
        val normalizedPath = if (posterPath.startsWith('/')) posterPath else "/$posterPath"
        val normalizedImageBaseUrl = tmdbProperties.imageBaseUrl.trimEnd('/')
        return "$normalizedImageBaseUrl/t/p/w780$normalizedPath"
    }

    private fun resolveSlug(title: String): String? {
        val normalized = SlugTextUtil.normalize(title).replace('_', '-')
        return normalized.ifBlank { null }
    }
}
