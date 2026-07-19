package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.games.GameDetailsResponse
import dev.marcal.mediapulse.server.api.games.GameExternalIdDto
import dev.marcal.mediapulse.server.api.games.GameImageDto
import dev.marcal.mediapulse.server.api.games.GameLibraryCardDto
import dev.marcal.mediapulse.server.api.games.GameSessionDto
import dev.marcal.mediapulse.server.api.games.GameSessionStatusDto
import dev.marcal.mediapulse.server.api.games.GamesLibraryResponse
import dev.marcal.mediapulse.server.api.games.GamesSearchResponse
import dev.marcal.mediapulse.server.api.games.GamesStatsResponse
import dev.marcal.mediapulse.server.model.EntityType
import jakarta.persistence.EntityManager
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Repository
import org.springframework.web.server.ResponseStatusException
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64

@Repository
class GameQueryRepository(
    private val entityManager: EntityManager,
    private val mediaCommentQueryRepository: MediaCommentQueryRepository,
    private val mediaRatingQueryRepository: MediaRatingQueryRepository,
) {
    fun library(
        limit: Int,
        cursor: String?,
    ): GamesLibraryResponse {
        val resolvedLimit = limit.coerceAtLeast(1)
        val (cursorActivityAt, cursorGameId) = parseCursor(cursor)
        val whereClause =
            if (cursorActivityAt != null && cursorGameId != null) {
                """
                WHERE (
                  COALESCE(latest_session_at, TIMESTAMP '1970-01-01 00:00:00') < :cursorActivityAt
                  OR (
                    COALESCE(latest_session_at, TIMESTAMP '1970-01-01 00:00:00') = :cursorActivityAt
                    AND game_id < :cursorGameId
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
                    WITH game_rollup AS (
                      SELECT
                        g.id AS game_id,
                        g.title,
                        g.original_title,
                        g.slug,
                        g.year,
                        g.cover_url,
                        COUNT(gs.id) AS session_count,
                        MAX(COALESCE(gs.ended_at, gs.started_at)) AS latest_session_at,
                        (
                          SELECT gs2.status
                          FROM game_sessions gs2
                          WHERE gs2.game_id = g.id
                          ORDER BY COALESCE(gs2.ended_at, gs2.started_at) DESC, gs2.id DESC
                          LIMIT 1
                        ) AS current_status
                      FROM games g
                      LEFT JOIN game_sessions gs ON gs.game_id = g.id
                      GROUP BY g.id
                    )
                    SELECT game_id, title, original_title, slug, year, cover_url, session_count, latest_session_at, current_status
                    FROM game_rollup
                    $whereClause
                    ORDER BY COALESCE(latest_session_at, TIMESTAMP '1970-01-01 00:00:00') DESC, game_id DESC
                    LIMIT :limitPlusOne
                    """.trimIndent(),
                ).apply {
                    if (cursorActivityAt != null && cursorGameId != null) {
                        setParameter("cursorActivityAt", Timestamp.from(cursorActivityAt))
                        setParameter("cursorGameId", cursorGameId)
                    }
                    setParameter("limitPlusOne", resolvedLimit + 1)
                }.resultList
                .map { row -> toGameLibraryCard(row as Array<*>) }

        val items = rows.take(resolvedLimit)
        val hasMore = rows.size > resolvedLimit
        return GamesLibraryResponse(
            items = items,
            nextCursor = if (hasMore) items.lastOrNull()?.let { buildCursor(it.latestSessionAt, it.gameId) } else null,
        )
    }

    fun search(
        query: String,
        limit: Int,
    ): GamesSearchResponse {
        val normalized = query.trim()
        if (normalized.isBlank()) return GamesSearchResponse(emptyList())
        val like = "%${normalized.lowercase()}%"
        val rows =
            entityManager
                .createNativeQuery(
                    """
                    WITH game_rollup AS (
                      SELECT
                        g.id AS game_id,
                        g.title,
                        g.original_title,
                        g.slug,
                        g.year,
                        g.cover_url,
                        COUNT(gs.id) AS session_count,
                        MAX(COALESCE(gs.ended_at, gs.started_at)) AS latest_session_at,
                        (
                          SELECT gs2.status
                          FROM game_sessions gs2
                          WHERE gs2.game_id = g.id
                          ORDER BY COALESCE(gs2.ended_at, gs2.started_at) DESC, gs2.id DESC
                          LIMIT 1
                        ) AS current_status
                      FROM games g
                      LEFT JOIN game_sessions gs ON gs.game_id = g.id
                      WHERE LOWER(g.title) LIKE :query OR LOWER(g.original_title) LIKE :query
                      GROUP BY g.id
                    )
                    SELECT game_id, title, original_title, slug, year, cover_url, session_count, latest_session_at, current_status
                    FROM game_rollup
                    ORDER BY COALESCE(latest_session_at, TIMESTAMP '1970-01-01 00:00:00') DESC, title ASC
                    LIMIT :limit
                    """.trimIndent(),
                ).setParameter("query", like)
                .setParameter("limit", limit.coerceAtLeast(1))
                .resultList
                .map { row -> toGameLibraryCard(row as Array<*>) }

        return GamesSearchResponse(rows)
    }

    fun stats(): GamesStatsResponse {
        val row =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      COUNT(DISTINCT g.id) AS total_games,
                      COUNT(gs.id) AS sessions_count,
                      COUNT(DISTINCT CASE WHEN latest.status = 'BACKLOG' THEN g.id END) AS backlog_count,
                      COUNT(DISTINCT CASE WHEN latest.status = 'PLAYING' THEN g.id END) AS active_count,
                      COUNT(DISTINCT CASE WHEN latest.status = 'COMPLETED' THEN g.id END) AS completed_count,
                      COUNT(DISTINCT CASE WHEN latest.status = 'ABANDONED' THEN g.id END) AS abandoned_count,
                      MAX(COALESCE(gs.ended_at, gs.started_at)) AS latest_session_at
                    FROM games g
                    LEFT JOIN game_sessions gs ON gs.game_id = g.id
                    LEFT JOIN LATERAL (
                      SELECT status
                      FROM game_sessions gs2
                      WHERE gs2.game_id = g.id
                      ORDER BY COALESCE(gs2.ended_at, gs2.started_at) DESC, gs2.id DESC
                      LIMIT 1
                    ) latest ON TRUE
                    """.trimIndent(),
                ).singleResult as Array<*>
        return GamesStatsResponse(
            totalGamesCount = (row[0] as Number).toLong(),
            sessionsCount = (row[1] as Number).toLong(),
            backlogCount = (row[2] as Number).toLong(),
            activeCount = (row[3] as Number).toLong(),
            completedCount = (row[4] as Number).toLong(),
            abandonedCount = (row[5] as Number).toLong(),
            latestSessionAt = asInstant(row[6]),
        )
    }

    fun getGameDetailsBySlug(slug: String): GameDetailsResponse {
        val gameId =
            entityManager
                .createNativeQuery("SELECT id FROM games WHERE slug = :slug LIMIT 1")
                .setParameter("slug", slug)
                .resultList
                .firstOrNull()
                ?.let { (it as Number).toLong() }
                ?: slug.toLongOrNull()
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found")
        return getGameDetails(gameId)
    }

    fun getGameDetails(gameId: Long): GameDetailsResponse {
        val base =
            entityManager
                .createNativeQuery(
                    """
                    SELECT id, title, original_title, slug, year, description, cover_url, igdb_id, steamgriddb_id
                    FROM games
                    WHERE id = :gameId
                    """.trimIndent(),
                ).setParameter("gameId", gameId)
                .resultList
                .firstOrNull() as Array<*>?
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Game not found")

        val images =
            entityManager
                .createNativeQuery(
                    """
                    SELECT id, url, kind, is_primary
                    FROM game_images
                    WHERE game_id = :gameId
                    ORDER BY is_primary DESC, id ASC
                    """.trimIndent(),
                ).setParameter("gameId", gameId)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    GameImageDto(
                        id = (fields[0] as Number).toLong(),
                        url = fields[1] as String,
                        kind = fields[2] as String,
                        isPrimary = fields[3] as Boolean,
                    )
                }

        val sessions =
            entityManager
                .createNativeQuery(
                    """
                    SELECT id, status, started_at, ended_at, notes
                    FROM game_sessions
                    WHERE game_id = :gameId
                    ORDER BY started_at DESC, id DESC
                    LIMIT 100
                    """.trimIndent(),
                ).setParameter("gameId", gameId)
                .resultList
                .map { row -> toGameSessionDto(row as Array<*>) }

        val externalIds =
            listOfNotNull(
                (base[7] as String?)?.let { GameExternalIdDto(provider = "IGDB", externalId = it) },
                (base[8] as String?)?.let { GameExternalIdDto(provider = "STEAMGRIDDB", externalId = it) },
            )

        return GameDetailsResponse(
            gameId = (base[0] as Number).toLong(),
            title = base[1] as String,
            originalTitle = base[2] as String,
            slug = base[3] as String?,
            year = (base[4] as Number?)?.toInt(),
            description = base[5] as String?,
            coverUrl = base[6] as String?,
            images = images,
            sessions = sessions,
            externalIds = externalIds,
            rating = mediaRatingQueryRepository.findByEntity(EntityType.GAME, gameId),
            comments = mediaCommentQueryRepository.findByEntity(EntityType.GAME, gameId),
        )
    }

    private fun toGameLibraryCard(fields: Array<*>): GameLibraryCardDto =
        GameLibraryCardDto(
            gameId = (fields[0] as Number).toLong(),
            title = fields[1] as String,
            originalTitle = fields[2] as String,
            slug = fields[3] as String?,
            year = (fields[4] as Number?)?.toInt(),
            coverUrl = fields[5] as String?,
            sessionCount = (fields[6] as Number).toLong(),
            latestSessionAt = asInstant(fields[7]),
            currentStatus = (fields[8] as String?)?.let { GameSessionStatusDto.valueOf(it) },
        )

    private fun toGameSessionDto(fields: Array<*>): GameSessionDto =
        GameSessionDto(
            sessionId = (fields[0] as Number).toLong(),
            status = GameSessionStatusDto.valueOf(fields[1] as String),
            startedAt = asInstant(fields[2])!!,
            endedAt = asInstant(fields[3]),
            notes = fields[4] as String?,
        )

    private fun asInstant(value: Any?): Instant? =
        when (value) {
            null -> null
            is Instant -> value
            is Timestamp -> value.toInstant()
            is java.util.Date -> value.toInstant()
            else -> null
        }

    private fun parseCursor(cursor: String?): Pair<Instant?, Long?> {
        if (cursor.isNullOrBlank()) return null to null
        return runCatching {
            val decoded = String(Base64.getUrlDecoder().decode(cursor))
            val parts = decoded.split("|")
            Instant.ofEpochMilli(parts[0].toLong()) to parts[1].toLong()
        }.getOrDefault(null to null)
    }

    private fun buildCursor(
        activityAt: Instant?,
        gameId: Long,
    ): String {
        val millis = activityAt?.toEpochMilli() ?: 0L
        return Base64.getUrlEncoder().withoutPadding().encodeToString("$millis|$gameId".toByteArray())
    }
}
