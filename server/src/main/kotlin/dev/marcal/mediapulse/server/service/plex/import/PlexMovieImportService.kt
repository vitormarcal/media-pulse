package dev.marcal.mediapulse.server.service.plex.import

import dev.marcal.mediapulse.server.integration.plex.PlexApiClient
import dev.marcal.mediapulse.server.integration.plex.dto.PlexGuid
import dev.marcal.mediapulse.server.integration.plex.dto.PlexLibrarySection
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.model.movie.MovieTitleSource
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.repository.crud.MovieTitleCrudRepository
import dev.marcal.mediapulse.server.service.plex.PlexMovieArtworkService
import dev.marcal.mediapulse.server.util.FingerprintUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PlexMovieImportService(
    private val plexApiClient: PlexApiClient,
    private val movieRepository: MovieRepository,
    private val movieTitleCrudRepository: MovieTitleCrudRepository,
    private val plexMovieArtworkService: PlexMovieArtworkService,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    data class ImportStats(
        var moviesSeen: Int = 0,
        var moviesUpserted: Int = 0,
    )

    suspend fun importAllMovies(
        sectionKey: String? = null,
        pageSize: Int = 200,
    ): ImportStats {
        val stats = ImportStats()
        val startedAt = System.nanoTime()

        logger.info(
            "Starting Plex movie library import. sectionKey={}, pageSize={}",
            sectionKey ?: "<all-sections>",
            pageSize,
        )

        val sections =
            if (sectionKey != null) {
                listOf(PlexLibrarySection(key = sectionKey, type = "movie"))
            } else {
                plexApiClient.listMovieSections()
            }

        logger.info("Plex movie sections loaded | sections={}", sections.size)

        for (section in sections) {
            var start = 0
            var total = Int.MAX_VALUE
            while (start < total) {
                logger.info("Plex movie page fetching | sectionKey={} start={} pageSize={}", section.key, start, pageSize)
                val (movies, tot) = plexApiClient.listMoviesPaged(section.key, start, pageSize)
                total = tot
                logger.info(
                    "Plex movie page loaded | sectionKey={} start={} movies={} total={}",
                    section.key,
                    start,
                    movies.size,
                    total,
                )
                if (movies.isEmpty()) break

                for ((index, m) in movies.withIndex()) {
                    stats.moviesSeen++
                    val movie =
                        upsertMovie(
                            title = m.title,
                            originalTitle = m.originalTitle,
                            year = m.year,
                            summary = m.summary,
                            slug = resolveSlug(m.slug),
                            guids = m.guids.orEmpty(),
                        )
                    plexMovieArtworkService.ensureMovieImagesFromPlex(
                        movie = movie,
                        images =
                            m.image.map { img ->
                                PlexMovieArtworkService.PlexMovieImageCandidate(
                                    url = img.url,
                                    isPoster = img.type.equals("coverPoster", ignoreCase = true),
                                )
                            },
                        fallbackThumbPath = m.thumb,
                    )
                    stats.moviesUpserted++
                    if (stats.moviesUpserted % 10 == 0 || index == movies.lastIndex) {
                        logger.info(
                            "Plex movie import progress | sectionKey={} sectionProcessed={} sectionTotal={} moviesUpserted={} elapsedMs={}",
                            section.key,
                            start + index + 1,
                            total,
                            stats.moviesUpserted,
                            (System.nanoTime() - startedAt) / 1_000_000,
                        )
                    }
                }

                start += movies.size
            }
        }

        logger.info(
            "Finished Plex movie library import. moviesSeen={}, moviesUpserted={}",
            stats.moviesSeen,
            stats.moviesUpserted,
        )

        return stats
    }

    @Transactional
    fun upsertMovie(
        title: String,
        originalTitle: String?,
        year: Int?,
        summary: String?,
        slug: String?,
        guids: List<PlexGuid>,
    ): Movie {
        val normalizedOriginal = originalTitle?.trim()?.ifBlank { null } ?: title
        val normalizedTitle = title.trim()
        val normalizedSummary = summary?.trim()?.ifBlank { null }
        val normalizedSlug = slug?.trim()?.ifBlank { null }

        val fingerprint = FingerprintUtil.movieFp(normalizedOriginal, year)
        val existing = resolveByExternalIds(guids) ?: movieRepository.findByFingerprint(fingerprint)

        val movie =
            if (existing == null) {
                movieRepository.save(
                    Movie(
                        originalTitle = normalizedOriginal,
                        year = year,
                        description = normalizedSummary,
                        slug = normalizedSlug,
                        fingerprint = fingerprint,
                    ),
                )
            } else {
                val merged =
                    mergeMovie(
                        existing = existing,
                        incomingYear = year,
                        incomingDescription = normalizedSummary,
                        incomingSlug = normalizedSlug,
                    )
                if (merged != existing) movieRepository.save(merged) else existing
            }

        movieTitleCrudRepository.insertIgnore(
            movieId = movie.id,
            title = movie.originalTitle,
            locale = null,
            source = MovieTitleSource.PLEX.name,
            isPrimary = true,
        )

        if (normalizedTitle != movie.originalTitle) {
            movieTitleCrudRepository.insertIgnore(
                movieId = movie.id,
                title = normalizedTitle,
                locale = null,
                source = MovieTitleSource.PLEX.name,
                isPrimary = false,
            )
        }

        persistExternalIds(movie.id, guids)
        return movie
    }

    private fun resolveByExternalIds(guids: List<PlexGuid>): Movie? {
        val candidates =
            externalIds(guids)
                .mapNotNull { (provider, externalId) ->
                    when (provider) {
                        Provider.TMDB -> movieRepository.findByTmdbId(externalId)
                        Provider.IMDB -> movieRepository.findByImdbId(externalId)
                        else -> null
                    }
                }.distinctBy { it.id }
        if (candidates.size > 1) {
            logger.warn("Conflicting Plex movie identities; falling back to fingerprint. movieIds={}", candidates.map { it.id })
            return null
        }
        return candidates.singleOrNull()
    }

    private fun externalIds(guids: List<PlexGuid>): List<Pair<Provider, String>> =
        guids
            .mapNotNull { guid ->
                val raw = guid.id.trim()
                when {
                    raw.startsWith("tmdb://", ignoreCase = true) -> Provider.TMDB to raw.substring(7).trim()
                    raw.startsWith("imdb://", ignoreCase = true) -> Provider.IMDB to raw.substring(7).trim()
                    else -> null
                }
            }.filter { (_, externalId) -> externalId.isNotBlank() }
            .distinct()

    private fun mergeMovie(
        existing: Movie,
        incomingYear: Int?,
        incomingDescription: String?,
        incomingSlug: String?,
    ): Movie {
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

    private fun resolveSlug(slug: String?): String? = slug?.trim()?.ifBlank { null }

    private fun persistExternalIds(
        movieId: Long,
        guids: List<PlexGuid>,
    ) {
        externalIds(guids).forEach { (provider, externalId) ->
            safeLink(movieId, provider, externalId)
        }
    }

    private fun safeLink(
        movieId: Long,
        provider: Provider,
        externalId: String,
    ) {
        val movie = movieRepository.findById(movieId).orElse(null) ?: return
        val currentExternalId =
            when (provider) {
                Provider.TMDB -> movie.tmdbId
                Provider.IMDB -> movie.imdbId
                else -> return
            }
        if (currentExternalId == externalId) return
        if (currentExternalId != null) {
            logger.warn(
                "Ignoring conflicting Plex movie identifier. movieId={}, provider={}, currentExternalId={}, incomingExternalId={}",
                movieId,
                provider,
                currentExternalId,
                externalId,
            )
            return
        }

        val linkedMovie =
            when (provider) {
                Provider.TMDB -> movieRepository.findByTmdbId(externalId)
                Provider.IMDB -> movieRepository.findByImdbId(externalId)
                else -> null
            }
        if (linkedMovie != null) {
            logger.warn(
                "Ignoring Plex movie identifier linked to another movie. movieId={}, linkedMovieId={}, provider={}, externalId={}",
                movieId,
                linkedMovie.id,
                provider,
                externalId,
            )
            return
        }

        movieRepository.save(
            when (provider) {
                Provider.TMDB -> movie.copy(tmdbId = externalId, updatedAt = Instant.now())
                Provider.IMDB -> movie.copy(imdbId = externalId, updatedAt = Instant.now())
                else -> movie
            },
        )
    }
}
