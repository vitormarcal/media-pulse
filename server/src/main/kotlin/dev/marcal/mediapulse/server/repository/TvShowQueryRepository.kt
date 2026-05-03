package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.shows.CurrentlyWatchingShowDto
import dev.marcal.mediapulse.server.api.shows.RangeDto
import dev.marcal.mediapulse.server.api.shows.ShowCardDto
import dev.marcal.mediapulse.server.api.shows.ShowCreditTypeDto
import dev.marcal.mediapulse.server.api.shows.ShowDetailsResponse
import dev.marcal.mediapulse.server.api.shows.ShowExternalIdDto
import dev.marcal.mediapulse.server.api.shows.ShowImageDto
import dev.marcal.mediapulse.server.api.shows.ShowLibraryCardDto
import dev.marcal.mediapulse.server.api.shows.ShowPersonCreditDto
import dev.marcal.mediapulse.server.api.shows.ShowProgressDto
import dev.marcal.mediapulse.server.api.shows.ShowSeasonDetailsResponse
import dev.marcal.mediapulse.server.api.shows.ShowSeasonDto
import dev.marcal.mediapulse.server.api.shows.ShowSeasonEpisodeDto
import dev.marcal.mediapulse.server.api.shows.ShowWatchDto
import dev.marcal.mediapulse.server.api.shows.ShowYearUnwatchedDto
import dev.marcal.mediapulse.server.api.shows.ShowYearWatchedDto
import dev.marcal.mediapulse.server.api.shows.ShowsByYearResponse
import dev.marcal.mediapulse.server.api.shows.ShowsByYearStatsDto
import dev.marcal.mediapulse.server.api.shows.ShowsLibraryResponse
import dev.marcal.mediapulse.server.api.shows.ShowsRecentResponse
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
    fun getShowPeople(showId: Long): List<ShowPersonCreditDto> =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  p.id,
                  p.tmdb_id,
                  p.name,
                  p.slug,
                  p.profile_url,
                  sc.credit_type,
                  NULLIF(sc.department, ''),
                  NULLIF(sc.job, ''),
                  NULLIF(sc.character_name, ''),
                  sc.billing_order
                FROM show_credits sc
                JOIN people p ON p.id = sc.person_id
                WHERE sc.show_id = :showId
                ORDER BY
                  CASE sc.credit_type WHEN 'CREW' THEN 0 ELSE 1 END,
                  CASE
                    WHEN sc.job = 'Director' THEN 0
                    WHEN sc.job IN ('Writer', 'Screenplay', 'Story Editor') THEN 1
                    ELSE 2
                  END,
                  COALESCE(sc.billing_order, 9999),
                  p.name ASC,
                  p.id ASC
                """.trimIndent(),
            ).setParameter("showId", showId)
            .resultList
            .map { row ->
                val fields = row as Array<*>
                ShowPersonCreditDto(
                    personId = (fields[0] as Number).toLong(),
                    tmdbId = fields[1] as String,
                    name = fields[2] as String,
                    slug = fields[3] as String,
                    profileUrl = fields[4] as String?,
                    creditType = ShowCreditTypeDto.valueOf(fields[5] as String),
                    department = fields[6] as String?,
                    job = fields[7] as String?,
                    characterName = fields[8] as String?,
                    billingOrder = (fields[9] as Number?)?.toInt(),
                )
            }

    fun library(
        limit: Int,
        cursor: String?,
        unwatched: Boolean,
    ): ShowsLibraryResponse {
        val resolvedLimit = limit.coerceAtLeast(1)
        val (cursorWatchedAt, cursorShowId) = parseRecentCursor(cursor)
        val filters =
            buildList {
                if (unwatched) {
                    add("watched_episodes_count = 0")
                }
                if (cursorWatchedAt != null && cursorShowId != null) {
                    add(
                        """
                        (
                          COALESCE(last_watched_at, TIMESTAMP '1970-01-01 00:00:00') < :cursorWatchedAt
                          OR (
                            COALESCE(last_watched_at, TIMESTAMP '1970-01-01 00:00:00') = :cursorWatchedAt
                            AND show_id < :cursorShowId
                          )
                        )
                        """.trimIndent(),
                    )
                }
            }
        val whereClause =
            if (filters.isNotEmpty()) {
                "WHERE " + filters.joinToString("\nAND ")
            } else {
                ""
            }

        val rows =
            entityManager
                .createNativeQuery(
                    """
                    WITH show_rollup AS (
                      SELECT
                        s.id AS show_id,
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
                        COUNT(DISTINCT te.id) AS episodes_count,
                        COUNT(DISTINCT CASE WHEN tew.id IS NOT NULL THEN te.id END) AS watched_episodes_count,
                        MAX(tew.watched_at) AS last_watched_at
                      FROM tv_shows s
                      LEFT JOIN tv_episodes te ON te.show_id = s.id
                      LEFT JOIN tv_episode_watches tew ON tew.episode_id = te.id
                      GROUP BY s.id, s.original_title, s.slug, s.year, s.cover_url
                    )
                    SELECT
                      show_id,
                      title,
                      original_title,
                      slug,
                      year,
                      cover_url,
                      watched_episodes_count,
                      episodes_count,
                      last_watched_at
                    FROM show_rollup
                    $whereClause
                    ORDER BY COALESCE(last_watched_at, TIMESTAMP '1970-01-01 00:00:00') DESC, show_id DESC
                    LIMIT :limitPlusOne
                    """.trimIndent(),
                ).apply {
                    if (cursorWatchedAt != null && cursorShowId != null) {
                        setParameter("cursorWatchedAt", Timestamp.from(cursorWatchedAt))
                        setParameter("cursorShowId", cursorShowId)
                    }
                    setParameter("limitPlusOne", resolvedLimit + 1)
                }.resultList
                .map { row ->
                    val fields = row as Array<*>
                    ShowLibraryCardDto(
                        showId = (fields[0] as Number).toLong(),
                        title = fields[1] as String,
                        originalTitle = fields[2] as String,
                        slug = fields[3] as String?,
                        year = (fields[4] as Number?)?.toInt(),
                        coverUrl = fields[5] as String?,
                        watchedEpisodesCount = (fields[6] as Number).toLong(),
                        episodesCount = (fields[7] as Number).toLong(),
                        lastWatchedAt = asInstant(fields[8]),
                    )
                }

        val hasMore = rows.size > resolvedLimit
        val items = rows.take(resolvedLimit)
        val nextCursor = items.lastOrNull()?.let { buildRecentCursor(it.lastWatchedAt, it.showId) }
        return ShowsLibraryResponse(
            items = items,
            nextCursor = if (hasMore) nextCursor else null,
        )
    }

    fun recent(
        limit: Int,
        cursor: String?,
    ): ShowsRecentResponse {
        val resolvedLimit = limit.coerceAtLeast(1)
        val (cursorWatchedAt, cursorShowId) = parseRecentCursor(cursor)
        val whereClause =
            if (cursorWatchedAt != null && cursorShowId != null) {
                """
                WHERE (
                  watched_at < :cursorWatchedAt
                  OR (watched_at = :cursorWatchedAt AND show_id < :cursorShowId)
                )
                """.trimIndent()
            } else {
                ""
            }
        val rows =
            entityManager
                .createNativeQuery(
                    """
                    WITH recent_shows AS (
                      SELECT
                        s.id AS show_id,
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
                    )
                    SELECT
                      show_id,
                      title,
                      original_title,
                      slug,
                      year,
                      cover_url,
                      watched_at
                    FROM recent_shows
                    $whereClause
                    ORDER BY watched_at DESC, show_id DESC
                    LIMIT :limitPlusOne
                    """.trimIndent(),
                ).apply {
                    if (cursorWatchedAt != null && cursorShowId != null) {
                        setParameter("cursorWatchedAt", cursorWatchedAt)
                        setParameter("cursorShowId", cursorShowId)
                    }
                    setParameter("limitPlusOne", resolvedLimit + 1)
                }.resultList
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
        val hasMore = rows.size > resolvedLimit
        val items = rows.take(resolvedLimit)
        val nextCursor = items.lastOrNull()?.let { buildRecentCursor(it.watchedAt, it.showId) }
        return ShowsRecentResponse(
            items = items,
            nextCursor = if (hasMore) nextCursor else null,
        )
    }

    fun currentlyWatching(
        limit: Int,
        activeSince: Instant,
    ): List<CurrentlyWatchingShowDto> =
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
                  progress_stats.episodes_count,
                  progress_stats.watched_episodes_count,
                  progress_stats.seasons_count,
                  progress_stats.completed_seasons_count,
                  progress_stats.last_watched_at
                FROM tv_shows s
                JOIN (
                  SELECT
                    season_rollup.show_id,
                    SUM(season_rollup.episodes_count) AS episodes_count,
                    SUM(season_rollup.watched_episodes_count) AS watched_episodes_count,
                    COUNT(*) AS seasons_count,
                    COUNT(*) FILTER (WHERE season_rollup.watched_episodes_count = season_rollup.episodes_count) AS completed_seasons_count,
                    MAX(season_rollup.last_watched_at) AS last_watched_at
                  FROM (
                    SELECT
                      te.show_id,
                      te.season_number,
                      COUNT(DISTINCT te.id) AS episodes_count,
                      COUNT(DISTINCT CASE WHEN tew.id IS NOT NULL THEN te.id END) AS watched_episodes_count,
                      MAX(tew.watched_at) AS last_watched_at
                    FROM tv_episodes te
                    LEFT JOIN tv_episode_watches tew ON tew.episode_id = te.id
                    GROUP BY te.show_id, te.season_number
                  ) season_rollup
                  GROUP BY season_rollup.show_id
                ) progress_stats ON progress_stats.show_id = s.id
                WHERE progress_stats.watched_episodes_count > 0
                  AND progress_stats.watched_episodes_count < progress_stats.episodes_count
                  AND progress_stats.last_watched_at >= :activeSince
                ORDER BY progress_stats.last_watched_at DESC, title ASC
                LIMIT :limit
                """.trimIndent(),
            ).setParameter("activeSince", activeSince)
            .setParameter("limit", limit)
            .resultList
            .map { row ->
                val fields = row as Array<*>
                val episodesCount = (fields[6] as Number).toLong()
                val watchedEpisodesCount = (fields[7] as Number).toLong()
                val seasonsCount = (fields[8] as Number).toLong()
                val completedSeasonsCount = (fields[9] as Number).toLong()
                CurrentlyWatchingShowDto(
                    showId = (fields[0] as Number).toLong(),
                    title = fields[1] as String,
                    originalTitle = fields[2] as String,
                    slug = fields[3] as String?,
                    year = (fields[4] as Number?)?.toInt(),
                    coverUrl = fields[5] as String?,
                    lastWatchedAt = asInstant(fields[10])!!,
                    progress =
                        ShowProgressDto(
                            episodesCount = episodesCount,
                            watchedEpisodesCount = watchedEpisodesCount,
                            seasonsCount = seasonsCount,
                            completedSeasonsCount = completedSeasonsCount,
                            completed = episodesCount > 0 && watchedEpisodesCount == episodesCount,
                            inProgress = watchedEpisodesCount > 0 && watchedEpisodesCount < episodesCount,
                        ),
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
                      te.season_title,
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
                        seasonTitle = fields[4] as String?,
                        episodeNumber = (fields[5] as Number?)?.toInt(),
                        watchedAt = asInstant(fields[6])!!,
                        source = fields[7] as String,
                    )
                }

        val seasons =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      te.season_number,
                      MAX(te.season_title) AS season_title,
                      COUNT(DISTINCT te.id) AS episodes_count,
                      COUNT(DISTINCT CASE WHEN tew.id IS NOT NULL THEN te.id END) AS watched_episodes_count,
                      MAX(tew.watched_at) AS last_watched_at
                    FROM tv_episodes te
                    LEFT JOIN tv_episode_watches tew ON tew.episode_id = te.id
                    WHERE te.show_id = :showId
                    GROUP BY te.season_number
                    ORDER BY te.season_number NULLS FIRST
                    """.trimIndent(),
                ).setParameter("showId", showId)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    val episodesCount = (fields[2] as Number).toLong()
                    val watchedEpisodesCount = (fields[3] as Number).toLong()
                    ShowSeasonDto(
                        seasonNumber = (fields[0] as Number?)?.toInt(),
                        seasonTitle = fields[1] as String?,
                        episodesCount = episodesCount,
                        watchedEpisodesCount = watchedEpisodesCount,
                        completed = episodesCount > 0 && watchedEpisodesCount == episodesCount,
                        lastWatchedAt = asInstant(fields[4]),
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

        val people = getShowPeople(showId)

        val progress =
            if (seasons.isEmpty()) {
                ShowProgressDto(
                    episodesCount = 0,
                    watchedEpisodesCount = 0,
                    seasonsCount = 0,
                    completedSeasonsCount = 0,
                    completed = false,
                    inProgress = false,
                )
            } else {
                val episodesCount = seasons.sumOf { it.episodesCount }
                val watchedEpisodesCount = seasons.sumOf { it.watchedEpisodesCount }
                val seasonsCount = seasons.size.toLong()
                val completedSeasonsCount = seasons.count { it.completed }.toLong()
                ShowProgressDto(
                    episodesCount = episodesCount,
                    watchedEpisodesCount = watchedEpisodesCount,
                    seasonsCount = seasonsCount,
                    completedSeasonsCount = completedSeasonsCount,
                    completed = episodesCount > 0 && watchedEpisodesCount == episodesCount,
                    inProgress = watchedEpisodesCount > 0 && watchedEpisodesCount < episodesCount,
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
            seasons = seasons,
            progress = progress,
            watches = watches,
            externalIds = externalIds,
            people = people,
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

    fun getShowSeasonDetailsBySlug(
        slug: String,
        seasonNumber: Int,
    ): ShowSeasonDetailsResponse {
        val base =
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
                      (
                        SELECT ei.external_id
                        FROM external_identifiers ei
                        WHERE ei.entity_type = 'SHOW'
                          AND ei.provider = 'TMDB'
                          AND ei.entity_id = s.id
                        ORDER BY ei.id ASC
                        LIMIT 1
                      ) AS tmdb_id
                    FROM tv_shows s
                    WHERE s.slug = :slug
                    LIMIT 1
                    """.trimIndent(),
                ).setParameter("slug", slug.trim())
                .resultList
                .firstOrNull() as Array<*>?
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found")

        val showId = (base[0] as Number).toLong()
        val episodes =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      te.id,
                      te.title,
                      te.season_number,
                      te.season_title,
                      te.episode_number,
                      te.summary,
                      te.duration_ms,
                      te.originally_available_at,
                      COUNT(tew.id) AS watch_count,
                      MAX(tew.watched_at) AS last_watched_at
                    FROM tv_episodes te
                    LEFT JOIN tv_episode_watches tew ON tew.episode_id = te.id
                    WHERE te.show_id = :showId
                      AND te.season_number = :seasonNumber
                    GROUP BY
                      te.id,
                      te.title,
                      te.season_number,
                      te.season_title,
                      te.episode_number,
                      te.summary,
                      te.duration_ms,
                      te.originally_available_at
                    ORDER BY te.episode_number NULLS LAST, te.id ASC
                    """.trimIndent(),
                ).setParameter("showId", showId)
                .setParameter("seasonNumber", seasonNumber)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    ShowSeasonEpisodeDto(
                        episodeId = (fields[0] as Number).toLong(),
                        title = fields[1] as String,
                        episodeNumber = (fields[4] as Number?)?.toInt(),
                        summary = fields[5] as String?,
                        durationMs = (fields[6] as Number?)?.toInt(),
                        originallyAvailableAt =
                            when (val value = fields[7]) {
                                is java.sql.Date -> value.toLocalDate()
                                is java.time.LocalDate -> value
                                else -> null
                            },
                        watchCount = (fields[8] as Number).toLong(),
                        lastWatchedAt = asInstant(fields[9]),
                    )
                }

        if (episodes.isEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Season not found")
        }

        val watchedEpisodesCount = episodes.count { it.watchCount > 0 }.toLong()
        val seasonTitle =
            entityManager
                .createNativeQuery(
                    """
                    SELECT MAX(te.season_title)
                    FROM tv_episodes te
                    WHERE te.show_id = :showId
                      AND te.season_number = :seasonNumber
                    """.trimIndent(),
                ).setParameter("showId", showId)
                .setParameter("seasonNumber", seasonNumber)
                .singleResult as String?

        return ShowSeasonDetailsResponse(
            showId = showId,
            showSlug = base[1] as String?,
            showTitle = base[2] as String,
            showOriginalTitle = base[3] as String,
            showYear = (base[4] as Number?)?.toInt(),
            showCoverUrl = base[5] as String?,
            showTmdbId = base[6] as String?,
            seasonNumber = seasonNumber,
            seasonTitle = seasonTitle,
            episodesCount = episodes.size.toLong(),
            watchedEpisodesCount = watchedEpisodesCount,
            completed = episodes.isNotEmpty() && watchedEpisodesCount == episodes.size.toLong(),
            lastWatchedAt = episodes.mapNotNull { it.lastWatchedAt }.maxOrNull(),
            episodes = episodes,
        )
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

    private fun parseRecentCursor(cursor: String?): Pair<Instant?, Long?> {
        if (cursor.isNullOrBlank()) return null to null
        val parts = cursor.split(":")
        require(parts.size == 4 && parts[0] == "ts" && parts[2] == "id") { "Invalid cursor format." }
        val watchedAt = Instant.ofEpochMilli(parts[1].toLongOrNull() ?: error("Invalid cursor value."))
        val showId = parts[3].toLongOrNull() ?: error("Invalid cursor value.")
        return watchedAt to showId
    }

    private fun buildRecentCursor(
        watchedAt: Instant?,
        showId: Long,
    ): String = "ts:${watchedAt?.toEpochMilli() ?: 0}:id:$showId"
}
