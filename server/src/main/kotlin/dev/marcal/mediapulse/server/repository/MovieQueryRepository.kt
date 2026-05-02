package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.movies.MovieCardDto
import dev.marcal.mediapulse.server.api.movies.MovieCollectionDto
import dev.marcal.mediapulse.server.api.movies.MovieCollectionMovieDto
import dev.marcal.mediapulse.server.api.movies.MovieCompanyDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MovieCompanyDto
import dev.marcal.mediapulse.server.api.movies.MovieCompanyTypeDto
import dev.marcal.mediapulse.server.api.movies.MovieCreditTypeDto
import dev.marcal.mediapulse.server.api.movies.MovieDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MovieExternalIdDto
import dev.marcal.mediapulse.server.api.movies.MovieImageDto
import dev.marcal.mediapulse.server.api.movies.MovieLibraryCardDto
import dev.marcal.mediapulse.server.api.movies.MovieListDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MovieListPreviewMovieDto
import dev.marcal.mediapulse.server.api.movies.MovieListSummaryDto
import dev.marcal.mediapulse.server.api.movies.MoviePersonCreditDto
import dev.marcal.mediapulse.server.api.movies.MoviePersonDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MoviePersonSuggestionDto
import dev.marcal.mediapulse.server.api.movies.MovieTermDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MovieTermDto
import dev.marcal.mediapulse.server.api.movies.MovieTermKindDto
import dev.marcal.mediapulse.server.api.movies.MovieTermSourceDto
import dev.marcal.mediapulse.server.api.movies.MovieTermSuggestionDto
import dev.marcal.mediapulse.server.api.movies.MovieWatchDto
import dev.marcal.mediapulse.server.api.movies.MovieYearUnwatchedDto
import dev.marcal.mediapulse.server.api.movies.MovieYearWatchedDto
import dev.marcal.mediapulse.server.api.movies.MoviesByYearResponse
import dev.marcal.mediapulse.server.api.movies.MoviesByYearStatsDto
import dev.marcal.mediapulse.server.api.movies.MoviesLibraryResponse
import dev.marcal.mediapulse.server.api.movies.MoviesRecentResponse
import dev.marcal.mediapulse.server.api.movies.MoviesSearchResponse
import dev.marcal.mediapulse.server.api.movies.MoviesStatsResponse
import dev.marcal.mediapulse.server.api.movies.MoviesSummaryResponse
import dev.marcal.mediapulse.server.api.movies.MoviesTotalStatsDto
import dev.marcal.mediapulse.server.api.movies.MoviesYearStatsDto
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
    fun library(
        limit: Int,
        cursor: String?,
    ): MoviesLibraryResponse {
        val resolvedLimit = limit.coerceAtLeast(1)
        val (cursorWatchedAt, cursorMovieId) = parseRecentCursor(cursor)
        val whereClause =
            if (cursorWatchedAt != null && cursorMovieId != null) {
                """
                WHERE (
                  COALESCE(last_watched_at, TIMESTAMP '1970-01-01 00:00:00') < :cursorWatchedAt
                  OR (
                    COALESCE(last_watched_at, TIMESTAMP '1970-01-01 00:00:00') = :cursorWatchedAt
                    AND movie_id < :cursorMovieId
                  )
                )
                """.trimIndent()
            } else {
                ""
            }

        val rows =
            entityManager
                .createNativeQuery(
                    """
                    WITH movie_rollup AS (
                      SELECT
                        m.id AS movie_id,
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
                        COUNT(mw.id) AS watch_count,
                        MAX(mw.watched_at) AS last_watched_at
                      FROM movies m
                      LEFT JOIN movie_watches mw ON mw.movie_id = m.id
                      GROUP BY m.id, m.original_title, m.slug, m.year, m.cover_url
                    )
                    SELECT
                      movie_id,
                      title,
                      original_title,
                      slug,
                      year,
                      cover_url,
                      watch_count,
                      last_watched_at
                    FROM movie_rollup
                    $whereClause
                    ORDER BY COALESCE(last_watched_at, TIMESTAMP '1970-01-01 00:00:00') DESC, movie_id DESC
                    LIMIT :limitPlusOne
                    """.trimIndent(),
                ).apply {
                    if (cursorWatchedAt != null && cursorMovieId != null) {
                        setParameter("cursorWatchedAt", Timestamp.from(cursorWatchedAt))
                        setParameter("cursorMovieId", cursorMovieId)
                    }
                    setParameter("limitPlusOne", resolvedLimit + 1)
                }.resultList
                .map { row ->
                    val fields = row as Array<*>
                    MovieLibraryCardDto(
                        movieId = (fields[0] as Number).toLong(),
                        title = fields[1] as String,
                        originalTitle = fields[2] as String,
                        slug = fields[3] as String?,
                        year = (fields[4] as Number?)?.toInt(),
                        coverUrl = fields[5] as String?,
                        watchCount = (fields[6] as Number).toLong(),
                        lastWatchedAt = asInstant(fields[7]),
                    )
                }

        val hasMore = rows.size > resolvedLimit
        val items = rows.take(resolvedLimit)
        val nextCursor = items.lastOrNull()?.let { buildRecentCursor(it.lastWatchedAt, it.movieId) }
        return MoviesLibraryResponse(
            items = items,
            nextCursor = if (hasMore) nextCursor else null,
        )
    }

    fun recent(
        limit: Int,
        cursor: String?,
    ): MoviesRecentResponse {
        val resolvedLimit = limit.coerceAtLeast(1)
        val (cursorWatchedAt, cursorMovieId) = parseRecentCursor(cursor)
        val whereClause =
            if (cursorWatchedAt != null && cursorMovieId != null) {
                """
                WHERE (
                  watched_at < :cursorWatchedAt
                  OR (watched_at = :cursorWatchedAt AND movie_id < :cursorMovieId)
                )
                """.trimIndent()
            } else {
                ""
            }
        val rows =
            entityManager
                .createNativeQuery(
                    """
                    WITH recent_movies AS (
                      SELECT
                        m.id AS movie_id,
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
                    )
                    SELECT
                      movie_id,
                      title,
                      original_title,
                      slug,
                      year,
                      cover_url,
                      watched_at
                    FROM recent_movies
                    $whereClause
                    ORDER BY watched_at DESC, movie_id DESC
                    LIMIT :limitPlusOne
                    """.trimIndent(),
                ).apply {
                    if (cursorWatchedAt != null && cursorMovieId != null) {
                        setParameter("cursorWatchedAt", cursorWatchedAt)
                        setParameter("cursorMovieId", cursorMovieId)
                    }
                    setParameter("limitPlusOne", resolvedLimit + 1)
                }.resultList
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
        val hasMore = rows.size > resolvedLimit
        val items = rows.take(resolvedLimit)
        val nextCursor = items.lastOrNull()?.let { buildRecentCursor(it.watchedAt, it.movieId) }
        return MoviesRecentResponse(
            items = items,
            nextCursor = if (hasMore) nextCursor else null,
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
                      m.cover_url,
                      mc.id AS collection_id,
                      mc.tmdb_id AS collection_tmdb_id,
                      mc.name AS collection_name,
                      mc.poster_url AS collection_poster_url,
                      mc.backdrop_url AS collection_backdrop_url
                    FROM movies m
                    LEFT JOIN movie_collections mc ON mc.id = m.collection_id
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

        val lists = getMovieLists(movieId)
        val companies = getMovieCompanies(movieId)
        val people = getMoviePeople(movieId)
        val terms = getMovieTerms(movieId)

        val collection =
            (base[7] as Number?)?.toLong()?.let { collectionId ->
                val collectionMovies =
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
                              EXISTS (
                                SELECT 1
                                FROM movie_watches mw
                                WHERE mw.movie_id = m.id
                              ) AS watched
                            FROM movies m
                            WHERE m.collection_id = :collectionId
                            ORDER BY m.year NULLS LAST, title ASC, m.id ASC
                            """.trimIndent(),
                        ).setParameter("collectionId", collectionId)
                        .resultList
                        .map { row ->
                            val fields = row as Array<*>
                            val memberId = (fields[0] as Number).toLong()
                            MovieCollectionMovieDto(
                                movieId = memberId,
                                title = fields[1] as String,
                                originalTitle = fields[2] as String,
                                slug = fields[3] as String?,
                                year = (fields[4] as Number?)?.toInt(),
                                coverUrl = fields[5] as String?,
                                watched = fields[6] as Boolean,
                                current = memberId == movieId,
                            )
                        }

                MovieCollectionDto(
                    id = collectionId,
                    tmdbId = base[8] as String,
                    name = base[9] as String,
                    posterUrl = base[10] as String?,
                    backdropUrl = base[11] as String?,
                    movies = collectionMovies,
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
            lists = lists,
            companies = companies,
            people = people,
            terms = terms,
            collection = collection,
        )
    }

    fun getMovieLists(movieId: Long): List<MovieListSummaryDto> =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  ml.id,
                  ml.name,
                  ml.slug,
                  ml.description,
                  ml.cover_movie_id,
                  cm.cover_url,
                  COUNT(mli2.id) AS item_count
                FROM movie_list_items mli
                JOIN movie_lists ml ON ml.id = mli.list_id
                LEFT JOIN movies cm ON cm.id = ml.cover_movie_id
                LEFT JOIN movie_list_items mli2 ON mli2.list_id = ml.id
                WHERE mli.movie_id = :movieId
                GROUP BY ml.id, ml.name, ml.slug, ml.description, ml.cover_movie_id, cm.cover_url
                ORDER BY ml.name ASC, ml.id ASC
                """.trimIndent(),
            ).setParameter("movieId", movieId)
            .resultList
            .let { rows ->
                val listIds = rows.map { ((it as Array<*>)[0] as Number).toLong() }
                val previewsByListId = getMovieListPreviewMovies(listIds)

                rows.map { row ->
                    val fields = row as Array<*>
                    val listId = (fields[0] as Number).toLong()
                    MovieListSummaryDto(
                        listId = listId,
                        name = fields[1] as String,
                        slug = fields[2] as String,
                        description = fields[3] as String?,
                        coverMovieId = (fields[4] as Number?)?.toLong(),
                        coverUrl = fields[5] as String?,
                        itemCount = (fields[6] as Number).toLong(),
                        previewMovies = previewsByListId[listId].orEmpty(),
                    )
                }
            }

    fun listMovieLists(): List<MovieListSummaryDto> =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  ml.id,
                  ml.name,
                  ml.slug,
                  ml.description,
                  ml.cover_movie_id,
                  cm.cover_url,
                  COUNT(mli.id) AS item_count
                FROM movie_lists ml
                LEFT JOIN movies cm ON cm.id = ml.cover_movie_id
                LEFT JOIN movie_list_items mli ON mli.list_id = ml.id
                GROUP BY ml.id, ml.name, ml.slug, ml.description, ml.cover_movie_id, cm.cover_url
                ORDER BY COALESCE(ml.updated_at, ml.created_at) DESC, ml.name ASC
                """.trimIndent(),
            ).resultList
            .let { rows ->
                val listIds = rows.map { ((it as Array<*>)[0] as Number).toLong() }
                val previewsByListId = getMovieListPreviewMovies(listIds)

                rows.map { row ->
                    val fields = row as Array<*>
                    toMovieListSummaryDto(
                        fields = fields,
                        previewMovies = previewsByListId[(fields[0] as Number).toLong()].orEmpty(),
                    )
                }
            }

    fun getMovieListSummary(listId: Long): MovieListSummaryDto? =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  ml.id,
                  ml.name,
                  ml.slug,
                  ml.description,
                  ml.cover_movie_id,
                  cm.cover_url,
                  COUNT(mli.id) AS item_count
                FROM movie_lists ml
                LEFT JOIN movies cm ON cm.id = ml.cover_movie_id
                LEFT JOIN movie_list_items mli ON mli.list_id = ml.id
                WHERE ml.id = :listId
                GROUP BY ml.id, ml.name, ml.slug, ml.description, ml.cover_movie_id, cm.cover_url
                LIMIT 1
                """.trimIndent(),
            ).setParameter("listId", listId)
            .resultList
            .firstOrNull()
            ?.let { row ->
                toMovieListSummaryDto(
                    fields = row as Array<*>,
                    previewMovies = getMovieListPreviewMovies(listOf(listId))[listId].orEmpty(),
                )
            }

    fun getMovieCompanies(movieId: Long): List<MovieCompanyDto> =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  mc.id,
                  mc.tmdb_id,
                  mc.name,
                  mc.slug,
                  mc.logo_url,
                  NULLIF(mc.origin_country, ''),
                  mca.company_type
                FROM movie_company_assignments mca
                JOIN movie_companies mc ON mc.id = mca.company_id
                WHERE mca.movie_id = :movieId
                ORDER BY mc.name ASC, mc.id ASC
                """.trimIndent(),
            ).setParameter("movieId", movieId)
            .resultList
            .map { row -> toMovieCompanyDto(row as Array<*>) }

    fun getMoviePeople(movieId: Long): List<MoviePersonCreditDto> =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  mp.id,
                  mp.tmdb_id,
                  mp.name,
                  mp.slug,
                  mp.profile_url,
                  mc.credit_type,
                  NULLIF(mc.department, ''),
                  NULLIF(mc.job, ''),
                  NULLIF(mc.character_name, ''),
                  mc.billing_order
                FROM movie_credits mc
                JOIN movie_people mp ON mp.id = mc.person_id
                WHERE mc.movie_id = :movieId
                ORDER BY
                  CASE mc.credit_type WHEN 'CREW' THEN 0 ELSE 1 END,
                  CASE
                    WHEN mc.job = 'Director' THEN 0
                    WHEN mc.job IN ('Writer', 'Screenplay', 'Story') THEN 1
                    ELSE 2
                  END,
                  COALESCE(mc.billing_order, 9999),
                  mp.name ASC,
                  mp.id ASC
                """.trimIndent(),
            ).setParameter("movieId", movieId)
            .resultList
            .map { row -> toMoviePersonCreditDto(row as Array<*>) }

    fun getMovieTerms(movieId: Long): List<MovieTermDto> =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  mt.id,
                  mt.name,
                  mt.slug,
                  mt.kind,
                  mta.source,
                  mt.hidden AS hidden_globally,
                  mta.hidden AS hidden_for_movie
                FROM movie_term_assignments mta
                JOIN movie_terms mt ON mt.id = mta.term_id
                WHERE mta.movie_id = :movieId
                ORDER BY
                  CASE mt.kind WHEN 'GENRE' THEN 0 ELSE 1 END,
                  CASE WHEN mt.hidden OR mta.hidden THEN 1 ELSE 0 END,
                  mt.name ASC,
                  mt.id ASC
                """.trimIndent(),
            ).setParameter("movieId", movieId)
            .resultList
            .map { row -> toMovieTermDto(row as Array<*>) }

    fun findTerm(termId: Long): MovieTermDto? =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  mt.id,
                  mt.name,
                  mt.slug,
                  mt.kind,
                  mt.source,
                  mt.hidden AS hidden_globally,
                  FALSE AS hidden_for_movie
                FROM movie_terms mt
                WHERE mt.id = :termId
                LIMIT 1
                """.trimIndent(),
            ).setParameter("termId", termId)
            .resultList
            .firstOrNull()
            ?.let { toMovieTermDto(it as Array<*>) }

    fun searchMovieTerms(
        query: String,
        kind: String,
        limit: Int,
    ): List<MovieTermSuggestionDto> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) return emptyList()

        return entityManager
            .createNativeQuery(
                """
                SELECT
                  mt.id,
                  mt.name,
                  mt.slug,
                  mt.kind,
                  mt.source,
                  mt.hidden
                FROM movie_terms mt
                WHERE mt.kind = :kind
                  AND mt.normalized_name LIKE :query
                ORDER BY
                  CASE WHEN mt.normalized_name = :exactQuery THEN 0 ELSE 1 END,
                  mt.hidden ASC,
                  mt.name ASC,
                  mt.id ASC
                LIMIT :limit
                """.trimIndent(),
            ).setParameter("kind", kind)
            .setParameter("query", "%$normalizedQuery%")
            .setParameter("exactQuery", normalizedQuery)
            .setParameter("limit", limit)
            .resultList
            .map { row -> toMovieTermSuggestionDto(row as Array<*>) }
    }

    fun getMoviePersonDetails(slug: String): MoviePersonDetailsResponse {
        val base =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      mp.id,
                      mp.tmdb_id,
                      mp.name,
                      mp.slug,
                      mp.profile_url,
                      COUNT(DISTINCT mc.movie_id) AS movie_count,
                      COUNT(DISTINCT CASE WHEN EXISTS (
                        SELECT 1
                        FROM movie_watches mw
                        WHERE mw.movie_id = mc.movie_id
                      ) THEN mc.movie_id END) AS watched_movies_count
                    FROM movie_people mp
                    JOIN movie_credits mc ON mc.person_id = mp.id
                    WHERE mp.slug = :slug
                    GROUP BY mp.id, mp.tmdb_id, mp.name, mp.slug, mp.profile_url
                    LIMIT 1
                    """.trimIndent(),
                ).setParameter("slug", slug.trim())
                .resultList
                .firstOrNull() as Array<*>?
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie person not found")

        val personId = (base[0] as Number).toLong()

        val roles =
            entityManager
                .createNativeQuery(
                    """
                    SELECT DISTINCT
                      CASE
                        WHEN mc.credit_type = 'CAST' THEN 'Elenco'
                        WHEN mc.job = 'Director' THEN 'Direção'
                        WHEN mc.job IN ('Writer', 'Screenplay', 'Story') THEN 'Roteiro'
                        ELSE COALESCE(NULLIF(mc.job, ''), 'Equipe')
                      END AS role_label
                    FROM movie_credits mc
                    WHERE mc.person_id = :personId
                    ORDER BY role_label ASC
                    """.trimIndent(),
                ).setParameter("personId", personId)
                .resultList
                .map { it as String }

        val movies =
            entityManager
                .createNativeQuery(
                    """
                    WITH movie_rollup AS (
                      SELECT
                        m.id AS movie_id,
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
                        COUNT(mw.id) AS watch_count,
                        MAX(mw.watched_at) AS last_watched_at
                      FROM movie_credits mc
                      JOIN movies m ON m.id = mc.movie_id
                      LEFT JOIN movie_watches mw ON mw.movie_id = m.id
                      WHERE mc.person_id = :personId
                      GROUP BY m.id, m.original_title, m.slug, m.year, m.cover_url
                    )
                    SELECT
                      movie_id,
                      title,
                      original_title,
                      slug,
                      year,
                      cover_url,
                      watch_count,
                      last_watched_at
                    FROM movie_rollup
                    ORDER BY COALESCE(last_watched_at, TIMESTAMP '1970-01-01 00:00:00') DESC, year DESC NULLS LAST, title ASC
                    """.trimIndent(),
                ).setParameter("personId", personId)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    MovieLibraryCardDto(
                        movieId = (fields[0] as Number).toLong(),
                        title = fields[1] as String,
                        originalTitle = fields[2] as String,
                        slug = fields[3] as String?,
                        year = (fields[4] as Number?)?.toInt(),
                        coverUrl = fields[5] as String?,
                        watchCount = (fields[6] as Number).toLong(),
                        lastWatchedAt = asInstant(fields[7]),
                    )
                }

        return MoviePersonDetailsResponse(
            personId = personId,
            tmdbId = base[1] as String,
            name = base[2] as String,
            slug = base[3] as String,
            profileUrl = base[4] as String?,
            roles = roles,
            movieCount = (base[5] as Number).toLong(),
            watchedMoviesCount = (base[6] as Number).toLong(),
            movies = movies,
        )
    }

    fun getMovieCompanyDetails(slug: String): MovieCompanyDetailsResponse {
        val base =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      mc.id,
                      mc.tmdb_id,
                      mc.name,
                      mc.slug,
                      mc.logo_url,
                      NULLIF(mc.origin_country, ''),
                      mca.company_type,
                      COUNT(DISTINCT mca.movie_id) AS movie_count,
                      COUNT(DISTINCT CASE WHEN EXISTS (
                        SELECT 1
                        FROM movie_watches mw
                        WHERE mw.movie_id = mca.movie_id
                      ) THEN mca.movie_id END) AS watched_movies_count
                    FROM movie_companies mc
                    JOIN movie_company_assignments mca ON mca.company_id = mc.id
                    WHERE mc.slug = :slug
                    GROUP BY mc.id, mc.tmdb_id, mc.name, mc.slug, mc.logo_url, mc.origin_country, mca.company_type
                    LIMIT 1
                    """.trimIndent(),
                ).setParameter("slug", slug.trim())
                .resultList
                .firstOrNull() as Array<*>?
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie company not found")

        val companyId = (base[0] as Number).toLong()
        val movies =
            entityManager
                .createNativeQuery(
                    """
                    WITH movie_rollup AS (
                      SELECT
                        m.id AS movie_id,
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
                        COUNT(mw.id) AS watch_count,
                        MAX(mw.watched_at) AS last_watched_at
                      FROM movie_company_assignments mca
                      JOIN movies m ON m.id = mca.movie_id
                      LEFT JOIN movie_watches mw ON mw.movie_id = m.id
                      WHERE mca.company_id = :companyId
                      GROUP BY m.id, m.original_title, m.slug, m.year, m.cover_url
                    )
                    SELECT
                      movie_id,
                      title,
                      original_title,
                      slug,
                      year,
                      cover_url,
                      watch_count,
                      last_watched_at
                    FROM movie_rollup
                    ORDER BY COALESCE(last_watched_at, TIMESTAMP '1970-01-01 00:00:00') DESC, year DESC NULLS LAST, title ASC
                    """.trimIndent(),
                ).setParameter("companyId", companyId)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    MovieLibraryCardDto(
                        movieId = (fields[0] as Number).toLong(),
                        title = fields[1] as String,
                        originalTitle = fields[2] as String,
                        slug = fields[3] as String?,
                        year = (fields[4] as Number?)?.toInt(),
                        coverUrl = fields[5] as String?,
                        watchCount = (fields[6] as Number).toLong(),
                        lastWatchedAt = asInstant(fields[7]),
                    )
                }

        return MovieCompanyDetailsResponse(
            companyId = companyId,
            tmdbId = base[1] as String,
            name = base[2] as String,
            slug = base[3] as String,
            logoUrl = base[4] as String?,
            originCountry = base[5] as String?,
            companyType = MovieCompanyTypeDto.valueOf(base[6] as String),
            movieCount = (base[7] as Number).toLong(),
            watchedMoviesCount = (base[8] as Number).toLong(),
            movies = movies,
        )
    }

    fun getMovieListDetails(slug: String): MovieListDetailsResponse {
        val base =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      ml.id,
                      ml.name,
                      ml.slug,
                      ml.description,
                      ml.cover_movie_id,
                      cm.cover_url,
                      COUNT(DISTINCT mli.movie_id) AS movie_count,
                      COUNT(DISTINCT CASE WHEN EXISTS (
                        SELECT 1
                        FROM movie_watches mw
                        WHERE mw.movie_id = mli.movie_id
                      ) THEN mli.movie_id END) AS watched_movies_count
                    FROM movie_lists ml
                    LEFT JOIN movies cm ON cm.id = ml.cover_movie_id
                    LEFT JOIN movie_list_items mli ON mli.list_id = ml.id
                    WHERE ml.slug = :slug
                    GROUP BY ml.id, ml.name, ml.slug, ml.description, ml.cover_movie_id, cm.cover_url
                    LIMIT 1
                    """.trimIndent(),
                ).setParameter("slug", slug.trim())
                .resultList
                .firstOrNull() as Array<*>?
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie list not found")

        val listId = (base[0] as Number).toLong()
        val movies =
            entityManager
                .createNativeQuery(
                    """
                    WITH movie_rollup AS (
                      SELECT
                        m.id AS movie_id,
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
                        COUNT(mw.id) AS watch_count,
                        MAX(mw.watched_at) AS last_watched_at,
                        MIN(mli.position) AS position
                      FROM movie_list_items mli
                      JOIN movies m ON m.id = mli.movie_id
                      LEFT JOIN movie_watches mw ON mw.movie_id = m.id
                      WHERE mli.list_id = :listId
                      GROUP BY m.id, m.original_title, m.slug, m.year, m.cover_url
                    )
                    SELECT movie_id, title, original_title, slug, year, cover_url, watch_count, last_watched_at
                    FROM movie_rollup
                    ORDER BY position ASC, title ASC
                    """.trimIndent(),
                ).setParameter("listId", listId)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    MovieLibraryCardDto(
                        movieId = (fields[0] as Number).toLong(),
                        title = fields[1] as String,
                        originalTitle = fields[2] as String,
                        slug = fields[3] as String?,
                        year = (fields[4] as Number?)?.toInt(),
                        coverUrl = fields[5] as String?,
                        watchCount = (fields[6] as Number).toLong(),
                        lastWatchedAt = asInstant(fields[7]),
                    )
                }

        return MovieListDetailsResponse(
            listId = listId,
            name = base[1] as String,
            slug = base[2] as String,
            description = base[3] as String?,
            coverMovieId = (base[4] as Number?)?.toLong(),
            coverUrl = base[5] as String?,
            movieCount = (base[6] as Number).toLong(),
            watchedMoviesCount = (base[7] as Number).toLong(),
            movies = movies,
        )
    }

    fun searchMoviePeople(
        query: String,
        limit: Int,
    ): List<MoviePersonSuggestionDto> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) return emptyList()

        return entityManager
            .createNativeQuery(
                """
                SELECT
                  mp.id,
                  mp.tmdb_id,
                  mp.name,
                  mp.slug,
                  mp.profile_url,
                  ARRAY_REMOVE(ARRAY_AGG(DISTINCT
                    CASE
                      WHEN mc.credit_type = 'CAST' THEN 'Elenco'
                      WHEN mc.job = 'Director' THEN 'Direção'
                      WHEN mc.job IN ('Writer', 'Screenplay', 'Story') THEN 'Roteiro'
                      ELSE COALESCE(NULLIF(mc.job, ''), 'Equipe')
                    END
                  ), NULL) AS role_labels
                FROM movie_people mp
                LEFT JOIN movie_credits mc ON mc.person_id = mp.id
                WHERE mp.normalized_name LIKE :query
                GROUP BY mp.id, mp.tmdb_id, mp.name, mp.slug, mp.profile_url
                ORDER BY
                  CASE WHEN mp.normalized_name = :exactQuery THEN 0 ELSE 1 END,
                  mp.name ASC,
                  mp.id ASC
                LIMIT :limit
                """.trimIndent(),
            ).setParameter("query", "%$normalizedQuery%")
            .setParameter("exactQuery", normalizedQuery)
            .setParameter("limit", limit)
            .resultList
            .map { row ->
                val fields = row as Array<*>
                val roleArray = fields[5] as java.sql.Array?
                MoviePersonSuggestionDto(
                    personId = (fields[0] as Number).toLong(),
                    tmdbId = fields[1] as String,
                    name = fields[2] as String,
                    slug = fields[3] as String,
                    profileUrl = fields[4] as String?,
                    roles = ((roleArray?.array as Array<*>?) ?: emptyArray<Any>()).mapNotNull { it as String? }.distinct(),
                )
            }
    }

    fun getMovieTermDetails(
        kind: String,
        slug: String,
    ): MovieTermDetailsResponse {
        val base =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      mt.id,
                      mt.name,
                      mt.slug,
                      mt.kind,
                      mt.source,
                      COUNT(mta.id) AS movie_count,
                      COUNT(CASE WHEN EXISTS (
                        SELECT 1
                        FROM movie_watches mw
                        WHERE mw.movie_id = mta.movie_id
                      ) THEN 1 END) AS watched_movies_count
                    FROM movie_terms mt
                    JOIN movie_term_assignments mta ON mta.term_id = mt.id
                    WHERE mt.kind = :kind
                      AND mt.slug = :slug
                      AND mt.hidden = FALSE
                      AND mta.hidden = FALSE
                    GROUP BY mt.id, mt.name, mt.slug, mt.kind, mt.source
                    LIMIT 1
                    """.trimIndent(),
                ).setParameter("kind", kind)
                .setParameter("slug", slug.trim())
                .resultList
                .firstOrNull() as Array<*>?
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie term not found")

        val termId = (base[0] as Number).toLong()
        val movies =
            entityManager
                .createNativeQuery(
                    """
                    WITH movie_rollup AS (
                      SELECT
                        m.id AS movie_id,
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
                        COUNT(mw.id) AS watch_count,
                        MAX(mw.watched_at) AS last_watched_at
                      FROM movie_term_assignments mta
                      JOIN movies m ON m.id = mta.movie_id
                      LEFT JOIN movie_watches mw ON mw.movie_id = m.id
                      WHERE mta.term_id = :termId
                        AND mta.hidden = FALSE
                      GROUP BY m.id, m.original_title, m.slug, m.year, m.cover_url
                    )
                    SELECT movie_id, title, original_title, slug, year, cover_url, watch_count, last_watched_at
                    FROM movie_rollup
                    ORDER BY COALESCE(last_watched_at, TIMESTAMP '1970-01-01 00:00:00') DESC, title ASC
                    """.trimIndent(),
                ).setParameter("termId", termId)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    MovieLibraryCardDto(
                        movieId = (fields[0] as Number).toLong(),
                        title = fields[1] as String,
                        originalTitle = fields[2] as String,
                        slug = fields[3] as String?,
                        year = (fields[4] as Number?)?.toInt(),
                        coverUrl = fields[5] as String?,
                        watchCount = (fields[6] as Number).toLong(),
                        lastWatchedAt = asInstant(fields[7]),
                    )
                }

        return MovieTermDetailsResponse(
            termId = termId,
            name = base[1] as String,
            slug = base[2] as String,
            kind = MovieTermKindDto.valueOf(base[3] as String),
            source = MovieTermSourceDto.valueOf(base[4] as String),
            movieCount = (base[5] as Number).toLong(),
            watchedMoviesCount = (base[6] as Number).toLong(),
            movies = movies,
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

    fun byYear(
        year: Int,
        start: Instant,
        end: Instant,
        limitWatched: Int,
        limitUnwatched: Int,
    ): MoviesByYearResponse {
        val statsRow =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      COUNT(*) AS watches_count,
                      COUNT(DISTINCT mw.movie_id) AS unique_movies_count
                    FROM movie_watches mw
                    WHERE mw.watched_at BETWEEN :s AND :e
                    """.trimIndent(),
                ).setParameter("s", start)
                .setParameter("e", end)
                .singleResult as Array<*>

        val watchesCount = (statsRow[0] as Number).toLong()
        val uniqueMoviesCount = (statsRow[1] as Number).toLong()

        val watched =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      m.id,
                      m.slug,
                      COALESCE((
                        SELECT mt.title
                        FROM movie_titles mt
                        WHERE mt.movie_id = m.id
                        ORDER BY mt.is_primary ASC, mt.id ASC
                        LIMIT 1
                      ), m.original_title) AS title,
                      m.original_title,
                      m.year,
                      m.cover_url,
                      COUNT(*) AS watch_count_in_year,
                      MIN(mw.watched_at) AS first_watched_at,
                      MAX(mw.watched_at) AS last_watched_at
                    FROM movies m
                    JOIN movie_watches mw ON mw.movie_id = m.id
                    WHERE mw.watched_at BETWEEN :s AND :e
                    GROUP BY m.id, m.slug, m.original_title, m.year, m.cover_url
                    ORDER BY MAX(mw.watched_at) DESC, title ASC
                    LIMIT :limitWatched
                    """.trimIndent(),
                ).setParameter("s", start)
                .setParameter("e", end)
                .setParameter("limitWatched", limitWatched)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    MovieYearWatchedDto(
                        movieId = (fields[0] as Number).toLong(),
                        slug = fields[1] as String?,
                        title = fields[2] as String,
                        originalTitle = fields[3] as String,
                        year = (fields[4] as Number?)?.toInt(),
                        coverUrl = fields[5] as String?,
                        watchCountInYear = (fields[6] as Number).toLong(),
                        firstWatchedAt = asInstant(fields[7])!!,
                        lastWatchedAt = asInstant(fields[8])!!,
                    )
                }

        val unwatched =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      m.id,
                      m.slug,
                      COALESCE((
                        SELECT mt.title
                        FROM movie_titles mt
                        WHERE mt.movie_id = m.id
                        ORDER BY mt.is_primary ASC, mt.id ASC
                        LIMIT 1
                      ), m.original_title) AS title,
                      m.original_title,
                      m.year,
                      m.cover_url
                    FROM movies m
                    WHERE NOT EXISTS (
                      SELECT 1
                      FROM movie_watches mw
                      WHERE mw.movie_id = m.id
                    )
                    ORDER BY title ASC
                    LIMIT :limitUnwatched
                    """.trimIndent(),
                ).setParameter("limitUnwatched", limitUnwatched)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    MovieYearUnwatchedDto(
                        movieId = (fields[0] as Number).toLong(),
                        slug = fields[1] as String?,
                        title = fields[2] as String,
                        originalTitle = fields[3] as String,
                        year = (fields[4] as Number?)?.toInt(),
                        coverUrl = fields[5] as String?,
                    )
                }

        return MoviesByYearResponse(
            year = year,
            range = RangeDto(start = start, end = end),
            stats =
                MoviesByYearStatsDto(
                    watchesCount = watchesCount,
                    uniqueMoviesCount = uniqueMoviesCount,
                    rewatchesCount = watchesCount - uniqueMoviesCount,
                ),
            watched = watched,
            unwatched = unwatched,
        )
    }

    fun stats(): MoviesStatsResponse {
        val totalRow =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      COUNT(*) AS watches_count,
                      COUNT(DISTINCT mw.movie_id) AS unique_movies_count
                    FROM movie_watches mw
                    """.trimIndent(),
                ).singleResult as Array<*>

        val unwatchedCount =
            (
                entityManager
                    .createNativeQuery(
                        """
                        SELECT COUNT(*)
                        FROM movies m
                        WHERE NOT EXISTS (
                          SELECT 1
                          FROM movie_watches mw
                          WHERE mw.movie_id = m.id
                        )
                        """.trimIndent(),
                    ).singleResult as Number
            ).toLong()

        val years =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      EXTRACT(YEAR FROM (mw.watched_at AT TIME ZONE 'UTC')) AS year,
                      COUNT(*) AS watches_count,
                      COUNT(DISTINCT mw.movie_id) AS unique_movies_count
                    FROM movie_watches mw
                    GROUP BY EXTRACT(YEAR FROM (mw.watched_at AT TIME ZONE 'UTC'))
                    ORDER BY year DESC
                    """.trimIndent(),
                ).resultList
                .map { row ->
                    val fields = row as Array<*>
                    val watchesCount = (fields[1] as Number).toLong()
                    val uniqueMoviesCount = (fields[2] as Number).toLong()
                    MoviesYearStatsDto(
                        year = (fields[0] as Number).toInt(),
                        watchesCount = watchesCount,
                        uniqueMoviesCount = uniqueMoviesCount,
                        rewatchesCount = watchesCount - uniqueMoviesCount,
                    )
                }

        val boundsRow =
            entityManager
                .createNativeQuery(
                    """
                    SELECT MAX(mw.watched_at), MIN(mw.watched_at)
                    FROM movie_watches mw
                    """.trimIndent(),
                ).singleResult as Array<*>

        return MoviesStatsResponse(
            total =
                MoviesTotalStatsDto(
                    watchesCount = (totalRow[0] as Number).toLong(),
                    uniqueMoviesCount = (totalRow[1] as Number).toLong(),
                ),
            unwatchedCount = unwatchedCount,
            years = years,
            latestWatchAt = asInstant(boundsRow[0]),
            firstWatchAt = asInstant(boundsRow[1]),
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

    private fun parseRecentCursor(cursor: String?): Pair<Instant?, Long?> {
        if (cursor.isNullOrBlank()) return null to null
        val parts = cursor.split(":")
        require(parts.size == 4 && parts[0] == "ts" && parts[2] == "id") { "Invalid cursor format." }
        val watchedAt = Instant.ofEpochMilli(parts[1].toLongOrNull() ?: error("Invalid cursor value."))
        val movieId = parts[3].toLongOrNull() ?: error("Invalid cursor value.")
        return watchedAt to movieId
    }

    private fun buildRecentCursor(
        watchedAt: Instant?,
        movieId: Long,
    ): String = "ts:${watchedAt?.toEpochMilli() ?: 0}:id:$movieId"

    private fun toMovieTermDto(fields: Array<*>): MovieTermDto {
        val hiddenGlobally = fields[5] as Boolean
        val hiddenForMovie = fields[6] as Boolean
        return MovieTermDto(
            id = (fields[0] as Number).toLong(),
            name = fields[1] as String,
            slug = fields[2] as String,
            kind = MovieTermKindDto.valueOf(fields[3] as String),
            source = MovieTermSourceDto.valueOf(fields[4] as String),
            hiddenGlobally = hiddenGlobally,
            hiddenForMovie = hiddenForMovie,
            active = !hiddenGlobally && !hiddenForMovie,
        )
    }

    private fun toMoviePersonCreditDto(fields: Array<*>): MoviePersonCreditDto =
        MoviePersonCreditDto(
            personId = (fields[0] as Number).toLong(),
            tmdbId = fields[1] as String,
            name = fields[2] as String,
            slug = fields[3] as String,
            profileUrl = fields[4] as String?,
            creditType = MovieCreditTypeDto.valueOf(fields[5] as String),
            department = fields[6] as String?,
            job = fields[7] as String?,
            characterName = fields[8] as String?,
            billingOrder = (fields[9] as Number?)?.toInt(),
        )

    private fun toMovieCompanyDto(fields: Array<*>): MovieCompanyDto =
        MovieCompanyDto(
            companyId = (fields[0] as Number).toLong(),
            tmdbId = fields[1] as String,
            name = fields[2] as String,
            slug = fields[3] as String,
            logoUrl = fields[4] as String?,
            originCountry = fields[5] as String?,
            companyType = MovieCompanyTypeDto.valueOf(fields[6] as String),
        )

    private fun toMovieListSummaryDto(
        fields: Array<*>,
        previewMovies: List<MovieListPreviewMovieDto> = emptyList(),
    ): MovieListSummaryDto =
        MovieListSummaryDto(
            listId = (fields[0] as Number).toLong(),
            name = fields[1] as String,
            slug = fields[2] as String,
            description = fields[3] as String?,
            coverMovieId = (fields[4] as Number?)?.toLong(),
            coverUrl = fields[5] as String?,
            itemCount = (fields[6] as Number).toLong(),
            previewMovies = previewMovies,
        )

    private fun getMovieListPreviewMovies(
        listIds: List<Long>,
        limit: Int = 3,
    ): Map<Long, List<MovieListPreviewMovieDto>> {
        if (listIds.isEmpty()) return emptyMap()

        val rows =
            entityManager
                .createNativeQuery(
                    """
                    WITH ranked_items AS (
                      SELECT
                        mli.list_id,
                        m.id AS movie_id,
                        COALESCE((
                          SELECT mt.title
                          FROM movie_titles mt
                          WHERE mt.movie_id = m.id
                          ORDER BY mt.is_primary ASC, mt.id ASC
                          LIMIT 1
                        ), m.original_title) AS title,
                        m.slug,
                        m.cover_url,
                        ROW_NUMBER() OVER (
                          PARTITION BY mli.list_id
                          ORDER BY mli.position ASC, m.id ASC
                        ) AS item_rank
                      FROM movie_list_items mli
                      JOIN movies m ON m.id = mli.movie_id
                      WHERE mli.list_id IN (:listIds)
                    )
                    SELECT list_id, movie_id, title, slug, cover_url
                    FROM ranked_items
                    WHERE item_rank <= :limit
                    ORDER BY list_id ASC, item_rank ASC
                    """.trimIndent(),
                ).setParameter("listIds", listIds)
                .setParameter("limit", limit)
                .resultList

        val previewsByListId = linkedMapOf<Long, MutableList<MovieListPreviewMovieDto>>()
        rows.forEach { row ->
            val fields = row as Array<*>
            val listId = (fields[0] as Number).toLong()
            previewsByListId.getOrPut(listId) { mutableListOf() }.add(
                MovieListPreviewMovieDto(
                    movieId = (fields[1] as Number).toLong(),
                    title = fields[2] as String,
                    slug = fields[3] as String?,
                    coverUrl = fields[4] as String?,
                ),
            )
        }

        return previewsByListId
    }

    private fun toMovieTermSuggestionDto(fields: Array<*>): MovieTermSuggestionDto =
        MovieTermSuggestionDto(
            id = (fields[0] as Number).toLong(),
            name = fields[1] as String,
            slug = fields[2] as String,
            kind = MovieTermKindDto.valueOf(fields[3] as String),
            source = MovieTermSourceDto.valueOf(fields[4] as String),
            hiddenGlobally = fields[5] as Boolean,
        )
}
