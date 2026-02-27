package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.movies.MovieCardDto
import dev.marcal.mediapulse.server.api.movies.MovieDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MovieExternalIdDto
import dev.marcal.mediapulse.server.api.movies.MovieImageDto
import dev.marcal.mediapulse.server.api.movies.MovieWatchDto
import dev.marcal.mediapulse.server.api.movies.MoviesSearchResponse
import dev.marcal.mediapulse.server.api.movies.MoviesSummaryResponse
import dev.marcal.mediapulse.server.api.movies.RangeDto
import jakarta.persistence.EntityManager
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Repository
import org.springframework.web.server.ResponseStatusException
import java.sql.Timestamp
import java.time.Instant

@Repository
class MovieQueryRepository(
    private val entityManager: EntityManager,
) {
    fun recent(limit: Int): List<MovieCardDto> =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  m.id,
                  COALESCE((
                    SELECT mt.title
                    FROM movie_titles mt
                    WHERE mt.movie_id = m.id
                    ORDER BY mt.is_primary ASC, mt.id ASC
                    LIMIT 1
                  ), m.original_title) AS title,
                  m.original_title,
                  m.slug,
                  m.year,
                  m.cover_url,
                  MAX(mw.watched_at) AS watched_at
                FROM movies m
                JOIN movie_watches mw ON mw.movie_id = m.id
                GROUP BY m.id, m.original_title, m.slug, m.year, m.cover_url
                ORDER BY MAX(mw.watched_at) DESC
                LIMIT :n
                """.trimIndent(),
            ).setParameter("n", limit)
            .resultList
            .map { row ->
                val fields = row as Array<*>
                MovieCardDto(
                    movieId = (fields[0] as Number).toLong(),
                    title = fields[1] as String,
                    originalTitle = fields[2] as String,
                    slug = fields[3] as String?,
                    year = (fields[4] as Number?)?.toInt(),
                    coverUrl = fields[5] as String?,
                    watchedAt = asInstant(fields[6]),
                )
            }

    fun getMovieDetails(movieId: Long): MovieDetailsResponse {
        val base =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      m.id,
                      COALESCE((
                        SELECT mt.title
                        FROM movie_titles mt
                        WHERE mt.movie_id = m.id
                        ORDER BY mt.is_primary ASC, mt.id ASC
                        LIMIT 1
                      ), m.original_title) AS title,
                      m.original_title,
                      m.slug,
                      m.year,
                      m.description,
                      m.cover_url
                    FROM movies m
                    WHERE m.id = :movieId
                    """.trimIndent(),
                ).setParameter("movieId", movieId)
                .resultList
                .firstOrNull() as Array<*>?
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found")

        val images =
            entityManager
                .createNativeQuery(
                    """
                    SELECT mi.id, mi.url, mi.is_primary
                    FROM movie_images mi
                    WHERE mi.movie_id = :movieId
                    ORDER BY mi.is_primary DESC, mi.id ASC
                    """.trimIndent(),
                ).setParameter("movieId", movieId)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    MovieImageDto(
                        id = (fields[0] as Number).toLong(),
                        url = fields[1] as String,
                        isPrimary = fields[2] as Boolean,
                    )
                }

        val watches =
            entityManager
                .createNativeQuery(
                    """
                    SELECT mw.id, mw.watched_at, mw.source
                    FROM movie_watches mw
                    WHERE mw.movie_id = :movieId
                    ORDER BY mw.watched_at DESC
                    LIMIT 100
                    """.trimIndent(),
                ).setParameter("movieId", movieId)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    MovieWatchDto(
                        watchId = (fields[0] as Number).toLong(),
                        watchedAt = asInstant(fields[1])!!,
                        source = fields[2] as String,
                    )
                }

        val externalIds =
            entityManager
                .createNativeQuery(
                    """
                    SELECT ei.provider, ei.external_id
                    FROM external_identifiers ei
                    WHERE ei.entity_type = 'MOVIE'
                      AND ei.entity_id = :movieId
                    ORDER BY ei.provider, ei.external_id
                    """.trimIndent(),
                ).setParameter("movieId", movieId)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    MovieExternalIdDto(
                        provider = fields[0] as String,
                        externalId = fields[1] as String,
                    )
                }

        return MovieDetailsResponse(
            movieId = (base[0] as Number).toLong(),
            title = base[1] as String,
            originalTitle = base[2] as String,
            slug = base[3] as String?,
            year = (base[4] as Number?)?.toInt(),
            description = base[5] as String?,
            coverUrl = base[6] as String?,
            images = images,
            watches = watches,
            externalIds = externalIds,
        )
    }

    fun getMovieDetailsBySlug(slug: String): MovieDetailsResponse {
        val movieId =
            (
                entityManager
                    .createNativeQuery(
                        """
                        SELECT m.id
                        FROM movies m
                        WHERE m.slug = :slug
                        LIMIT 1
                        """.trimIndent(),
                    ).setParameter("slug", slug.trim())
                    .resultList
                    .firstOrNull() as Number?
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found")
            ).toLong()

        return getMovieDetails(movieId)
    }

    fun search(
        q: String,
        limit: Int,
    ): MoviesSearchResponse {
        val like = "%${q.lowercase()}%"

        val rows =
            entityManager
                .createNativeQuery(
                    """
                    SELECT DISTINCT
                      m.id,
                      COALESCE((
                        SELECT mt2.title
                        FROM movie_titles mt2
                        WHERE mt2.movie_id = m.id
                        ORDER BY mt2.is_primary ASC, mt2.id ASC
                        LIMIT 1
                      ), m.original_title) AS title,
                      m.original_title,
                      m.slug,
                      m.year,
                      m.cover_url,
                      (
                        SELECT MAX(mw.watched_at)
                        FROM movie_watches mw
                        WHERE mw.movie_id = m.id
                      ) AS watched_at
                    FROM movies m
                    LEFT JOIN movie_titles mt ON mt.movie_id = m.id
                    WHERE LOWER(m.original_title) LIKE :q
                       OR LOWER(COALESCE(m.slug, '')) LIKE :q
                       OR LOWER(COALESCE(mt.title, '')) LIKE :q
                    ORDER BY watched_at DESC NULLS LAST, m.original_title ASC
                    LIMIT :n
                    """.trimIndent(),
                ).setParameter("q", like)
                .setParameter("n", limit)
                .resultList

        val movies =
            rows.map { row ->
                val fields = row as Array<*>
                MovieCardDto(
                    movieId = (fields[0] as Number).toLong(),
                    title = fields[1] as String,
                    originalTitle = fields[2] as String,
                    slug = fields[3] as String?,
                    year = (fields[4] as Number?)?.toInt(),
                    coverUrl = fields[5] as String?,
                    watchedAt = asInstant(fields[6]),
                )
            }

        return MoviesSearchResponse(movies)
    }

    fun summary(
        start: Instant,
        end: Instant,
    ): MoviesSummaryResponse {
        val watchesCount =
            (
                entityManager
                    .createNativeQuery(
                        """
                        SELECT COUNT(*)
                        FROM movie_watches mw
                        WHERE mw.watched_at BETWEEN :s AND :e
                        """.trimIndent(),
                    ).setParameter("s", start)
                    .setParameter("e", end)
                    .singleResult as Number
            ).toLong()

        val uniqueMoviesCount =
            (
                entityManager
                    .createNativeQuery(
                        """
                        SELECT COUNT(DISTINCT mw.movie_id)
                        FROM movie_watches mw
                        WHERE mw.watched_at BETWEEN :s AND :e
                        """.trimIndent(),
                    ).setParameter("s", start)
                    .setParameter("e", end)
                    .singleResult as Number
            ).toLong()

        return MoviesSummaryResponse(
            range = RangeDto(start = start, end = end),
            watchesCount = watchesCount,
            uniqueMoviesCount = uniqueMoviesCount,
        )
    }

    private fun asInstant(value: Any?): Instant? =
        when (value) {
            null -> null
            is Instant -> value
            is Timestamp -> value.toInstant()
            is java.util.Date -> value.toInstant()
            else -> null
        }
}
