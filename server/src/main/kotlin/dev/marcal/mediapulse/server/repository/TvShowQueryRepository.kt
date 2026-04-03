package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.shows.RangeDto
import dev.marcal.mediapulse.server.api.shows.ShowCardDto
import dev.marcal.mediapulse.server.api.shows.ShowDetailsResponse
import dev.marcal.mediapulse.server.api.shows.ShowExternalIdDto
import dev.marcal.mediapulse.server.api.shows.ShowImageDto
import dev.marcal.mediapulse.server.api.shows.ShowWatchDto
import dev.marcal.mediapulse.server.api.shows.ShowYearUnwatchedDto
import dev.marcal.mediapulse.server.api.shows.ShowYearWatchedDto
import dev.marcal.mediapulse.server.api.shows.ShowsByYearResponse
import dev.marcal.mediapulse.server.api.shows.ShowsByYearStatsDto
import dev.marcal.mediapulse.server.api.shows.ShowsSearchResponse
import dev.marcal.mediapulse.server.api.shows.ShowsStatsResponse
import dev.marcal.mediapulse.server.api.shows.ShowsSummaryResponse
import dev.marcal.mediapulse.server.api.shows.ShowsTotalStatsDto
import dev.marcal.mediapulse.server.api.shows.ShowsYearStatsDto
import jakarta.persistence.EntityManager
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Repository
import org.springframework.web.server.ResponseStatusException
import java.sql.Timestamp
import java.time.Instant

@Repository
class TvShowQueryRepository(
    private val entityManager: EntityManager,
) {
    fun recent(limit: Int): List<ShowCardDto> =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  s.id,
                  COALESCE((
                    SELECT st.title
                    FROM tv_show_titles st
                    WHERE st.show_id = s.id
                    ORDER BY st.is_primary ASC, st.id ASC
                    LIMIT 1
                  ), s.original_title) AS title,
                  s.original_title,
                  s.slug,
                  s.year,
                  s.cover_url,
                  MAX(tew.watched_at) AS watched_at
                FROM tv_shows s
                JOIN tv_episodes te ON te.show_id = s.id
                JOIN tv_episode_watches tew ON tew.episode_id = te.id
                GROUP BY s.id, s.original_title, s.slug, s.year, s.cover_url
                ORDER BY MAX(tew.watched_at) DESC
                LIMIT :n
                """.trimIndent(),
            ).setParameter("n", limit)
            .resultList
            .map { row ->
                val fields = row as Array<*>
                ShowCardDto(
                    showId = (fields[0] as Number).toLong(),
                    title = fields[1] as String,
                    originalTitle = fields[2] as String,
                    slug = fields[3] as String?,
                    year = (fields[4] as Number?)?.toInt(),
                    coverUrl = fields[5] as String?,
                    watchedAt = asInstant(fields[6]),
                )
            }

    fun getShowDetails(showId: Long): ShowDetailsResponse {
        val base =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      s.id,
                      COALESCE((
                        SELECT st.title
                        FROM tv_show_titles st
                        WHERE st.show_id = s.id
                        ORDER BY st.is_primary ASC, st.id ASC
                        LIMIT 1
                      ), s.original_title) AS title,
                      s.original_title,
                      s.slug,
                      s.year,
                      s.description,
                      s.cover_url
                    FROM tv_shows s
                    WHERE s.id = :showId
                    """.trimIndent(),
                ).setParameter("showId", showId)
                .resultList
                .firstOrNull() as Array<*>?
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found")

        val images =
            entityManager
                .createNativeQuery(
                    """
                    SELECT tsi.id, tsi.url, tsi.is_primary
                    FROM tv_show_images tsi
                    WHERE tsi.show_id = :showId
                    ORDER BY tsi.is_primary DESC, tsi.id ASC
                    """.trimIndent(),
                ).setParameter("showId", showId)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    ShowImageDto(
                        id = (fields[0] as Number).toLong(),
                        url = fields[1] as String,
                        isPrimary = fields[2] as Boolean,
                    )
                }

        val watches =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      tew.id,
                      te.id,
                      te.title,
                      te.season_number,
                      te.episode_number,
                      tew.watched_at,
                      tew.source
                    FROM tv_episode_watches tew
                    JOIN tv_episodes te ON te.id = tew.episode_id
                    WHERE te.show_id = :showId
                    ORDER BY tew.watched_at DESC
                    LIMIT 100
                    """.trimIndent(),
                ).setParameter("showId", showId)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    ShowWatchDto(
                        watchId = (fields[0] as Number).toLong(),
                        episodeId = (fields[1] as Number).toLong(),
                        episodeTitle = fields[2] as String,
                        seasonNumber = (fields[3] as Number?)?.toInt(),
                        episodeNumber = (fields[4] as Number?)?.toInt(),
                        watchedAt = asInstant(fields[5])!!,
                        source = fields[6] as String,
                    )
                }

        val externalIds =
            entityManager
                .createNativeQuery(
                    """
                    SELECT ei.provider, ei.external_id
                    FROM external_identifiers ei
                    WHERE ei.entity_type = 'SHOW'
                      AND ei.entity_id = :showId
                    ORDER BY ei.provider, ei.external_id
                    """.trimIndent(),
                ).setParameter("showId", showId)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    ShowExternalIdDto(
                        provider = fields[0] as String,
                        externalId = fields[1] as String,
                    )
                }

        return ShowDetailsResponse(
            showId = (base[0] as Number).toLong(),
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

    fun getShowDetailsBySlug(slug: String): ShowDetailsResponse {
        val showId =
            (
                entityManager
                    .createNativeQuery(
                        """
                        SELECT s.id
                        FROM tv_shows s
                        WHERE s.slug = :slug
                        LIMIT 1
                        """.trimIndent(),
                    ).setParameter("slug", slug.trim())
                    .resultList
                    .firstOrNull() as Number?
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found")
            ).toLong()

        return getShowDetails(showId)
    }

    fun search(
        q: String,
        limit: Int,
    ): ShowsSearchResponse {
        val like = "%${q.lowercase()}%"

        val rows =
            entityManager
                .createNativeQuery(
                    """
                    SELECT DISTINCT
                      s.id,
                      COALESCE((
                        SELECT st2.title
                        FROM tv_show_titles st2
                        WHERE st2.show_id = s.id
                        ORDER BY st2.is_primary ASC, st2.id ASC
                        LIMIT 1
                      ), s.original_title) AS title,
                      s.original_title,
                      s.slug,
                      s.year,
                      s.cover_url,
                      (
                        SELECT MAX(tew.watched_at)
                        FROM tv_episode_watches tew
                        JOIN tv_episodes te2 ON te2.id = tew.episode_id
                        WHERE te2.show_id = s.id
                      ) AS watched_at
                    FROM tv_shows s
                    LEFT JOIN tv_show_titles st ON st.show_id = s.id
                    WHERE LOWER(s.original_title) LIKE :q
                       OR LOWER(COALESCE(s.slug, '')) LIKE :q
                       OR LOWER(COALESCE(st.title, '')) LIKE :q
                    ORDER BY watched_at DESC NULLS LAST, s.original_title ASC
                    LIMIT :n
                    """.trimIndent(),
                ).setParameter("q", like)
                .setParameter("n", limit)
                .resultList

        return ShowsSearchResponse(
            shows =
                rows.map { row ->
                    val fields = row as Array<*>
                    ShowCardDto(
                        showId = (fields[0] as Number).toLong(),
                        title = fields[1] as String,
                        originalTitle = fields[2] as String,
                        slug = fields[3] as String?,
                        year = (fields[4] as Number?)?.toInt(),
                        coverUrl = fields[5] as String?,
                        watchedAt = asInstant(fields[6]),
                    )
                },
        )
    }

    fun summary(
        start: Instant,
        end: Instant,
    ): ShowsSummaryResponse {
        val watchesCount =
            (
                entityManager
                    .createNativeQuery(
                        """
                        SELECT COUNT(*)
                        FROM tv_episode_watches tew
                        WHERE tew.watched_at BETWEEN :s AND :e
                        """.trimIndent(),
                    ).setParameter("s", start)
                    .setParameter("e", end)
                    .singleResult as Number
            ).toLong()

        val uniqueShowsCount =
            (
                entityManager
                    .createNativeQuery(
                        """
                        SELECT COUNT(DISTINCT te.show_id)
                        FROM tv_episode_watches tew
                        JOIN tv_episodes te ON te.id = tew.episode_id
                        WHERE tew.watched_at BETWEEN :s AND :e
                        """.trimIndent(),
                    ).setParameter("s", start)
                    .setParameter("e", end)
                    .singleResult as Number
            ).toLong()

        return ShowsSummaryResponse(
            range = RangeDto(start = start, end = end),
            watchesCount = watchesCount,
            uniqueShowsCount = uniqueShowsCount,
        )
    }

    fun byYear(
        year: Int,
        start: Instant,
        end: Instant,
        limitWatched: Int,
        limitUnwatched: Int,
    ): ShowsByYearResponse {
        val statsRow =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      COUNT(*) AS watches_count,
                      COUNT(DISTINCT te.show_id) AS unique_shows_count
                    FROM tv_episode_watches tew
                    JOIN tv_episodes te ON te.id = tew.episode_id
                    WHERE tew.watched_at BETWEEN :s AND :e
                    """.trimIndent(),
                ).setParameter("s", start)
                .setParameter("e", end)
                .singleResult as Array<*>

        val watchesCount = (statsRow[0] as Number).toLong()
        val uniqueShowsCount = (statsRow[1] as Number).toLong()

        val watched =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      s.id,
                      s.slug,
                      COALESCE((
                        SELECT st.title
                        FROM tv_show_titles st
                        WHERE st.show_id = s.id
                        ORDER BY st.is_primary ASC, st.id ASC
                        LIMIT 1
                      ), s.original_title) AS title,
                      s.original_title,
                      s.year,
                      s.cover_url,
                      COUNT(*) AS watch_count_in_year,
                      MIN(tew.watched_at) AS first_watched_at,
                      MAX(tew.watched_at) AS last_watched_at
                    FROM tv_shows s
                    JOIN tv_episodes te ON te.show_id = s.id
                    JOIN tv_episode_watches tew ON tew.episode_id = te.id
                    WHERE tew.watched_at BETWEEN :s AND :e
                    GROUP BY s.id, s.slug, s.original_title, s.year, s.cover_url
                    ORDER BY MAX(tew.watched_at) DESC, title ASC
                    LIMIT :limitWatched
                    """.trimIndent(),
                ).setParameter("s", start)
                .setParameter("e", end)
                .setParameter("limitWatched", limitWatched)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    ShowYearWatchedDto(
                        showId = (fields[0] as Number).toLong(),
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
                      s.id,
                      s.slug,
                      COALESCE((
                        SELECT st.title
                        FROM tv_show_titles st
                        WHERE st.show_id = s.id
                        ORDER BY st.is_primary ASC, st.id ASC
                        LIMIT 1
                      ), s.original_title) AS title,
                      s.original_title,
                      s.year,
                      s.cover_url
                    FROM tv_shows s
                    WHERE NOT EXISTS (
                      SELECT 1
                      FROM tv_episodes te
                      JOIN tv_episode_watches tew ON tew.episode_id = te.id
                      WHERE te.show_id = s.id
                    )
                    ORDER BY title ASC
                    LIMIT :limitUnwatched
                    """.trimIndent(),
                ).setParameter("limitUnwatched", limitUnwatched)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    ShowYearUnwatchedDto(
                        showId = (fields[0] as Number).toLong(),
                        slug = fields[1] as String?,
                        title = fields[2] as String,
                        originalTitle = fields[3] as String,
                        year = (fields[4] as Number?)?.toInt(),
                        coverUrl = fields[5] as String?,
                    )
                }

        return ShowsByYearResponse(
            year = year,
            range = RangeDto(start = start, end = end),
            stats =
                ShowsByYearStatsDto(
                    watchesCount = watchesCount,
                    uniqueShowsCount = uniqueShowsCount,
                    rewatchesCount = watchesCount - uniqueShowsCount,
                ),
            watched = watched,
            unwatched = unwatched,
        )
    }

    fun stats(): ShowsStatsResponse {
        val totalRow =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      COUNT(*) AS watches_count,
                      COUNT(DISTINCT te.show_id) AS unique_shows_count
                    FROM tv_episode_watches tew
                    JOIN tv_episodes te ON te.id = tew.episode_id
                    """.trimIndent(),
                ).singleResult as Array<*>

        val unwatchedCount =
            (
                entityManager
                    .createNativeQuery(
                        """
                        SELECT COUNT(*)
                        FROM tv_shows s
                        WHERE NOT EXISTS (
                          SELECT 1
                          FROM tv_episodes te
                          JOIN tv_episode_watches tew ON tew.episode_id = te.id
                          WHERE te.show_id = s.id
                        )
                        """.trimIndent(),
                    ).singleResult as Number
            ).toLong()

        val years =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      EXTRACT(YEAR FROM (tew.watched_at AT TIME ZONE 'UTC')) AS year,
                      COUNT(*) AS watches_count,
                      COUNT(DISTINCT te.show_id) AS unique_shows_count
                    FROM tv_episode_watches tew
                    JOIN tv_episodes te ON te.id = tew.episode_id
                    GROUP BY EXTRACT(YEAR FROM (tew.watched_at AT TIME ZONE 'UTC'))
                    ORDER BY year DESC
                    """.trimIndent(),
                ).resultList
                .map { row ->
                    val fields = row as Array<*>
                    val watchesCount = (fields[1] as Number).toLong()
                    val uniqueShowsCount = (fields[2] as Number).toLong()
                    ShowsYearStatsDto(
                        year = (fields[0] as Number).toInt(),
                        watchesCount = watchesCount,
                        uniqueShowsCount = uniqueShowsCount,
                        rewatchesCount = watchesCount - uniqueShowsCount,
                    )
                }

        val boundsRow =
            entityManager
                .createNativeQuery(
                    """
                    SELECT MAX(tew.watched_at), MIN(tew.watched_at)
                    FROM tv_episode_watches tew
                    """.trimIndent(),
                ).singleResult as Array<*>

        return ShowsStatsResponse(
            total =
                ShowsTotalStatsDto(
                    watchesCount = (totalRow[0] as Number).toLong(),
                    uniqueShowsCount = (totalRow[1] as Number).toLong(),
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
}
