package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.config.TmdbProperties
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.integration.tmdb.TmdbImageClient
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.model.movie.MovieTitleSource
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCollectionCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.repository.crud.MovieTitleCrudRepository
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import dev.marcal.mediapulse.server.util.FingerprintUtil
import dev.marcal.mediapulse.server.util.SlugTextUtil
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class ManualMovieCatalogService(
    private val movieRepository: MovieRepository,
    private val movieTitleCrudRepository: MovieTitleCrudRepository,
    private val movieCollectionCrudRepository: MovieCollectionCrudRepository,
    private val movieImageCrudRepository: MovieImageCrudRepository,
    private val externalIdentifierRepository: ExternalIdentifierRepository,
    private val tmdbApiClient: TmdbApiClient,
    private val tmdbImageClient: TmdbImageClient,
    private val imageStorageService: ImageStorageService,
    private val tmdbProperties: TmdbProperties,
) {
    data class TmdbImageCandidate(
        val key: String,
        val label: String,
        val path: String,
        val imageUrl: String,
        val suggestedAsPrimary: Boolean,
    )

    data class TmdbImageSelection(
        val selectedKeys: Set<String>,
        val primaryKey: String? = null,
    )

    data class TmdbImageAssignmentResult(
        val insertedCount: Int,
        val primaryImageUrl: String? = null,
    )

    data class MovieCatalogUpsertRequest(
        val title: String,
        val year: Int? = null,
        val tmdbId: String? = null,
        val imdbId: String? = null,
    )

    data class MovieCatalogResult(
        val movie: Movie,
        val created: Boolean,
        val coverAssigned: Boolean,
    )

    data class TmdbMovieSnapshot(
        val tmdbId: String,
        val title: String?,
        val originalTitle: String?,
        val imdbId: String?,
        val overview: String?,
        val releaseYear: Int?,
        val posterPath: String?,
        val backdropPath: String?,
        val posterUrl: String?,
        val backdropUrl: String?,
        val collection: TmdbMovieCollectionSnapshot? = null,
    )

    data class TmdbMovieCollectionSnapshot(
        val tmdbId: String,
        val name: String,
        val posterUrl: String?,
        val backdropUrl: String?,
    )

    fun resolveOrCreate(request: MovieCatalogUpsertRequest): MovieCatalogResult {
        val normalizedTitle = request.title.trim().ifBlank { throw IllegalArgumentException("title deve ser preenchido") }
        val normalizedTmdbId = request.tmdbId?.trim()?.ifBlank { null }
        val normalizedImdbId = request.imdbId?.trim()?.ifBlank { null }
        val tmdbSnapshot = normalizedTmdbId?.let { fetchTmdbMovieSnapshot(it) }
        val resolvedOriginalTitle = tmdbSnapshot?.originalTitle ?: normalizedTitle
        val resolvedYear = request.year ?: tmdbSnapshot?.releaseYear
        val resolvedDescription = tmdbSnapshot?.overview
        val resolvedSlug = resolveSlug(tmdbSnapshot?.title ?: resolvedOriginalTitle)

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
        tmdbSnapshot
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
        (normalizedImdbId ?: tmdbSnapshot?.imdbId)?.let { safeLink(movieAfterMetadata.id, Provider.IMDB, it) }
        val movieAfterCollection = maybeAssignTmdbCollection(movieAfterMetadata, tmdbSnapshot)

        val coverAssigned = maybeAssignTmdbImages(movie = movieAfterCollection, tmdbSnapshot = tmdbSnapshot).primaryImageUrl != null
        val refreshedMovie = movieRepository.findById(movieAfterCollection.id).orElse(movieAfterCollection)

        return MovieCatalogResult(
            movie = refreshedMovie,
            created = created,
            coverAssigned = coverAssigned,
        )
    }

    fun fetchTmdbMovieSnapshot(tmdbId: String): TmdbMovieSnapshot? {
        val normalizedTmdbId = tmdbId.trim().ifBlank { return null }
        val tmdbDetails = tmdbApiClient.fetchMovieDetails(normalizedTmdbId) ?: return null
        return TmdbMovieSnapshot(
            tmdbId = normalizedTmdbId,
            title = tmdbDetails.title,
            originalTitle = tmdbDetails.originalTitle,
            imdbId = tmdbDetails.imdbId,
            overview = tmdbDetails.overview,
            releaseYear = tmdbDetails.releaseYear,
            posterPath = tmdbDetails.posterPath,
            backdropPath = tmdbDetails.backdropPath,
            posterUrl = tmdbDetails.posterPath?.let(::buildTmdbImageUrl),
            backdropUrl = tmdbDetails.backdropPath?.let(::buildTmdbImageUrl),
            collection =
                tmdbDetails.collection?.let { collection ->
                    TmdbMovieCollectionSnapshot(
                        tmdbId = collection.tmdbId,
                        name = collection.name,
                        posterUrl = collection.posterPath?.let(::buildTmdbImageUrl),
                        backdropUrl = collection.backdropPath?.let(::buildTmdbImageUrl),
                    )
                },
        )
    }

    fun addMovieTitle(
        movieId: Long,
        title: String,
        isPrimary: Boolean = false,
    ) {
        movieTitleCrudRepository.insertIgnore(
            movieId = movieId,
            title = title,
            locale = null,
            source = MovieTitleSource.MANUAL.name,
            isPrimary = isPrimary,
        )
    }

    fun linkExternalIdIfAvailable(
        movieId: Long,
        provider: Provider,
        externalId: String,
    ) {
        safeLink(movieId, provider, externalId)
    }

    fun assignTmdbImages(
        movie: Movie,
        tmdbSnapshot: TmdbMovieSnapshot,
    ): Boolean = maybeAssignTmdbImages(movie, tmdbSnapshot).primaryImageUrl != null

    fun buildTmdbImageCandidates(tmdbSnapshot: TmdbMovieSnapshot): List<TmdbImageCandidate> =
        buildList {
            tmdbSnapshot.posterPath?.let { path ->
                add(
                    TmdbImageCandidate(
                        key = "poster",
                        label = "Poster",
                        path = path,
                        imageUrl = buildTmdbImageUrl(path),
                        suggestedAsPrimary = true,
                    ),
                )
            }
            tmdbSnapshot.backdropPath?.let { path ->
                add(
                    TmdbImageCandidate(
                        key = "backdrop",
                        label = "Backdrop",
                        path = path,
                        imageUrl = buildTmdbImageUrl(path),
                        suggestedAsPrimary = false,
                    ),
                )
            }
        }.distinctBy { it.path }

    fun assignSelectedTmdbImages(
        movie: Movie,
        tmdbSnapshot: TmdbMovieSnapshot,
        selection: TmdbImageSelection? = null,
    ): TmdbImageAssignmentResult = maybeAssignTmdbImages(movie, tmdbSnapshot, selection)

    fun assignTmdbCollection(
        movie: Movie,
        tmdbSnapshot: TmdbMovieSnapshot,
    ): Movie = maybeAssignTmdbCollection(movie, tmdbSnapshot)

    fun resolveMovieSlug(title: String): String? = resolveSlug(title)

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
        val existing = externalIdentifierRepository.findByProviderAndExternalId(provider, externalId)
        if (existing != null) {
            if (existing.entityType == EntityType.MOVIE && existing.entityId == movieId) {
                return
            }

            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "${provider.name} $externalId já está vinculado a outra entidade",
            )
        }

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
        tmdbSnapshot: TmdbMovieSnapshot?,
        selection: TmdbImageSelection? = null,
    ): TmdbImageAssignmentResult {
        if (tmdbSnapshot == null) return TmdbImageAssignmentResult(insertedCount = 0)

        val allCandidates = buildTmdbImageCandidates(tmdbSnapshot)
        val candidates =
            if (selection == null || selection.selectedKeys.isEmpty()) {
                allCandidates
            } else {
                allCandidates.filter { selection.selectedKeys.contains(it.key) }
            }

        if (candidates.isEmpty()) return TmdbImageAssignmentResult(insertedCount = 0)

        val savedImages = mutableListOf<Pair<String, TmdbImageCandidate>>()

        candidates.forEach { candidate ->
            val localPath =
                runCatching {
                    val image = tmdbImageClient.downloadImage(candidate.imageUrl)
                    val fileHint = "${movie.originalTitle}_${DigestUtils.sha1Hex(candidate.imageUrl).take(12)}"
                    imageStorageService.saveImageForMovie(
                        image = image,
                        provider = "TMDB",
                        movieId = movie.id,
                        fileNameHint = fileHint,
                    )
                }.getOrNull() ?: return@forEach
            savedImages += localPath to candidate
        }

        if (savedImages.isEmpty()) return TmdbImageAssignmentResult(insertedCount = 0)

        val explicitPrimaryKey = selection?.primaryKey
        val primaryLocalPath =
            explicitPrimaryKey
                ?.let { key -> savedImages.firstOrNull { it.second.key == key }?.first }
                ?: savedImages.firstOrNull { it.second.suggestedAsPrimary }?.first
                ?: savedImages.first().first

        savedImages.forEach { (localPath, _) ->
            movieImageCrudRepository.insertIgnore(
                movieId = movie.id,
                url = localPath,
                isPrimary = false,
            )
        }

        val shouldPromotePrimary =
            selection?.primaryKey != null || !movieImageCrudRepository.existsByMovieIdAndIsPrimaryTrue(movie.id)

        if (shouldPromotePrimary) {
            movieImageCrudRepository.lockMovieRowForPrimaryUpdate(movie.id)
            movieImageCrudRepository.clearPrimaryForMovie(movie.id)
            movieImageCrudRepository.markPrimaryForMovie(movie.id, primaryLocalPath)
            movieRepository.save(movie.copy(coverUrl = primaryLocalPath, updatedAt = Instant.now()))
        }

        return TmdbImageAssignmentResult(
            insertedCount = savedImages.size,
            primaryImageUrl = if (shouldPromotePrimary) primaryLocalPath else null,
        )
    }

    private fun maybeAssignTmdbCollection(
        movie: Movie,
        tmdbSnapshot: TmdbMovieSnapshot?,
    ): Movie {
        val collection = tmdbSnapshot?.collection ?: return movie
        val collectionId =
            movieCollectionCrudRepository.upsertFromTmdb(
                tmdbId = collection.tmdbId,
                name = collection.name,
                posterUrl = collection.posterUrl,
                backdropUrl = collection.backdropUrl,
            )

        if (movie.collectionId == collectionId && movie.collectionCheckedAt != null) return movie

        val now = Instant.now()
        return movieRepository.save(movie.copy(collectionId = collectionId, collectionCheckedAt = now, updatedAt = now))
    }

    fun buildTmdbImageUrl(posterPath: String): String {
        val normalizedPath = if (posterPath.startsWith('/')) posterPath else "/$posterPath"
        val normalizedImageBaseUrl = tmdbProperties.imageBaseUrl.trimEnd('/')
        return "$normalizedImageBaseUrl/t/p/w780$normalizedPath"
    }

    private fun resolveSlug(title: String): String? {
        val normalized = SlugTextUtil.normalize(title).replace('_', '-')
        return normalized.ifBlank { null }
    }
}
