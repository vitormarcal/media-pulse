package dev.marcal.mediapulse.server.repository.query

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.api.timeline.TimelineDomain
import dev.marcal.mediapulse.server.api.timeline.TimelineEvent
import dev.marcal.mediapulse.server.api.timeline.TimelineKind
import dev.marcal.mediapulse.server.api.timeline.TimelineLink
import dev.marcal.mediapulse.server.api.timeline.TimelineMonth
import dev.marcal.mediapulse.server.api.timeline.TimelinePrecision
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

data class TimelineRow(
    val event: TimelineEvent,
    val sortAt: Instant,
)

@Repository
class TimelineQueryRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val mapper: ObjectMapper,
) {
    fun events(
        domain: TimelineDomain?,
        kind: TimelineKind?,
        before: Instant?,
        cursorAt: Instant?,
        cursorId: String?,
        fetchLimit: Int,
    ): List<TimelineRow> =
        jdbc.query(
            "$EVENTS, page AS MATERIALIZED (SELECT * FROM events " + FILTER +
                """
                  AND (CAST(:before AS timestamptz) IS NULL OR occurred_at < :before)
                  AND (CAST(:cursorAt AS timestamptz) IS NULL OR (occurred_at, id COLLATE "C") < (:cursorAt, CAST(:cursorId AS text) COLLATE "C"))
                ORDER BY occurred_at DESC, id COLLATE "C" DESC LIMIT :fetchLimit
                )
                SELECT e.*, (occurred_at AT TIME ZONE :zone)::date AS event_date,
                  CASE WHEN domain = 'MUSIC' AND kind = 'ACTIVITY' THEN $MUSIC_LINKS ELSE related_items END AS page_related_items
                FROM page e ORDER BY occurred_at DESC, id COLLATE "C" DESC
                """.trimIndent(),
            params(domain, kind) +
                mapOf(
                    "before" to before?.let(Timestamp::from),
                    "cursorAt" to cursorAt?.let(Timestamp::from),
                    "cursorId" to cursorId,
                    "fetchLimit" to fetchLimit,
                ),
        ) { rs, _ ->
            val at = rs.getTimestamp("occurred_at").toInstant()
            val precision = TimelinePrecision.valueOf(rs.getString("precision"))
            TimelineRow(
                TimelineEvent(
                    id = rs.getString("id"),
                    domain = TimelineDomain.valueOf(rs.getString("domain")),
                    kind = TimelineKind.valueOf(rs.getString("kind")),
                    date = rs.getDate("event_date").toLocalDate(),
                    occurredAt = at.takeIf { precision == TimelinePrecision.INSTANT },
                    precision = precision,
                    action = rs.getString("action"),
                    title = rs.getString("title"),
                    subtitle = rs.getString("subtitle"),
                    href = rs.getString("href"),
                    imageUrl = rs.getString("image_url"),
                    relatedItems = mapper.readValue(rs.getString("page_related_items"), object : TypeReference<List<TimelineLink>>() {}),
                ),
                at,
            )
        }

    fun periods(
        domain: TimelineDomain?,
        kind: TimelineKind?,
    ): List<TimelineMonth> =
        jdbc.query(
            "$EVENTS SELECT to_char(occurred_at AT TIME ZONE :zone, 'YYYY-MM') AS month, count(*) AS total FROM events " + FILTER +
                " GROUP BY month ORDER BY month DESC",
            params(domain, kind),
        ) { rs, _ -> TimelineMonth(rs.getString("month"), rs.getLong("total")) }

    private fun params(
        domain: TimelineDomain?,
        kind: TimelineKind?,
    ): Map<String, Any?> = mapOf("domain" to domain?.name, "kind" to kind?.name, "zone" to "America/Sao_Paulo")

    private companion object {
        const val FILTER = "WHERE (CAST(:domain AS text) IS NULL OR domain = :domain) AND (CAST(:kind AS text) IS NULL OR kind = :kind)"
        val MUSIC_LINKS =
            """
            COALESCE((SELECT jsonb_agg(link ORDER BY link->>'title', link->>'href') FROM (
            SELECT DISTINCT jsonb_build_object('title', a.title, 'href', '/music/albums/' || a.id, 'imageUrl', a.cover_url,
            'subtitle', (SELECT string_agg(aa.credited_name || aa.join_phrase, '' ORDER BY aa.position) FROM album_artists aa WHERE aa.album_id = a.id)) AS link
            FROM track_playbacks p JOIN albums a ON a.id = p.album_id WHERE p.played_at >= e.occurred_at AND p.played_at < (((e.occurred_at AT TIME ZONE :zone)::date + 1)::timestamp AT TIME ZONE :zone)
            UNION
            SELECT DISTINCT jsonb_build_object('title', ar.name, 'href', '/music/artists/' || ar.id) AS link
            FROM track_playbacks p JOIN tracks t ON t.id = p.track_id JOIN artists ar ON ar.id = t.artist_id
            WHERE p.played_at >= e.occurred_at AND p.played_at < (((e.occurred_at AT TIME ZONE :zone)::date + 1)::timestamp AT TIME ZONE :zone)
            ) links), '[]'::jsonb)
            """.trimIndent()
        val EVENTS =
            """
            WITH music_days AS (
                SELECT (played_at AT TIME ZONE :zone)::date AS day, count(*) AS plays, min(album_id) AS first_album
                FROM track_playbacks GROUP BY day
            ), events AS (
                SELECT 'music-day:' || d.day AS id, 'MUSIC'::text AS domain, 'ACTIVITY'::text AS kind,
                       d.day::timestamp AT TIME ZONE :zone AS occurred_at, 'DAY'::text AS precision, 'Ouviu' AS action,
                       'Música do dia' AS title, d.plays || ' reproduções' AS subtitle, '/music' AS href, (SELECT cover_url FROM albums WHERE id = d.first_album) AS image_url,
                       '[]'::jsonb AS related_items
                FROM music_days d
                WHERE true
                UNION ALL
                SELECT 'movie-watch:' || w.id AS id, 'MOVIES'::text AS domain, 'ACTIVITY'::text AS kind,
                       w.watched_at AS occurred_at, 'INSTANT'::text AS precision, 'Assistiu' AS action,
                       COALESCE((SELECT title FROM movie_titles WHERE movie_id = m.id ORDER BY is_primary DESC, id LIMIT 1), m.original_title) AS title, NULL::text AS subtitle, '/movies/' || COALESCE(m.slug, m.id::text) AS href, m.cover_url AS image_url,
                       '[]'::jsonb AS related_items
                FROM movie_watches w JOIN movies m ON m.id = w.movie_id
                WHERE true
                UNION ALL
                SELECT 'episode-watch:' || w.id AS id, 'SHOWS'::text AS domain, 'ACTIVITY'::text AS kind,
                       w.watched_at AS occurred_at, 'INSTANT'::text AS precision, 'Assistiu' AS action,
                       COALESCE((SELECT title FROM tv_show_titles WHERE show_id = s.id ORDER BY is_primary DESC, id LIMIT 1), s.original_title) AS title, concat_ws(' · ', CASE WHEN e.season_number IS NOT NULL THEN 'T' || e.season_number END, CASE WHEN e.episode_number IS NOT NULL THEN 'Ep. ' || e.episode_number END, e.title) AS subtitle, '/shows/' || COALESCE(s.slug, s.id::text) AS href, s.cover_url AS image_url,
                       '[]'::jsonb AS related_items
                FROM tv_episode_watches w JOIN tv_episodes e ON e.id = w.episode_id JOIN tv_shows s ON s.id = e.show_id
                WHERE true
                UNION ALL
                SELECT 'book-start:' || r.id AS id, 'BOOKS'::text AS domain, 'ACTIVITY'::text AS kind,
                       r.started_at AS occurred_at, 'DAY'::text AS precision, 'Começou a ler' AS action,
                       b.title AS title, NULL::text AS subtitle, '/books/' || COALESCE(b.slug, b.id::text) AS href, COALESCE(ed.cover_url, b.cover_url) AS image_url,
                       '[]'::jsonb AS related_items
                FROM book_reads r JOIN books b ON b.id = r.book_id LEFT JOIN book_editions ed ON ed.id = r.edition_id
                WHERE r.started_at IS NOT NULL AND r.status <> 'WANT_TO_READ'
                UNION ALL
                SELECT 'book-finish:' || r.id AS id, 'BOOKS'::text AS domain, 'ACTIVITY'::text AS kind,
                       r.finished_at AS occurred_at, 'DAY'::text AS precision, CASE WHEN r.status = 'READ' THEN 'Terminou de ler' ELSE 'Encerrou a leitura' END AS action,
                       b.title AS title, NULL::text AS subtitle, '/books/' || COALESCE(b.slug, b.id::text) AS href, COALESCE(ed.cover_url, b.cover_url) AS image_url,
                       '[]'::jsonb AS related_items
                FROM book_reads r JOIN books b ON b.id = r.book_id LEFT JOIN book_editions ed ON ed.id = r.edition_id
                WHERE r.finished_at IS NOT NULL AND r.status <> 'WANT_TO_READ'
                UNION ALL
                SELECT 'magazine-start:' || r.id AS id, 'MAGAZINES'::text AS domain, 'ACTIVITY'::text AS kind,
                       r.started_at::timestamp AT TIME ZONE :zone AS occurred_at, 'DAY'::text AS precision, 'Começou a ler' AS action,
                       p.name AS title, concat_ws(' · ', i.number, i.cover_date) AS subtitle, '/magazines/' || i.id AS href, i.cover_url AS image_url,
                       '[]'::jsonb AS related_items
                FROM magazine_reads r JOIN magazine_issues i ON i.id = r.issue_id JOIN magazine_publications p ON p.id = i.publication_id
                WHERE r.started_at IS NOT NULL
                UNION ALL
                SELECT 'magazine-finish:' || r.id AS id, 'MAGAZINES'::text AS domain, 'ACTIVITY'::text AS kind,
                       r.finished_at::timestamp AT TIME ZONE :zone AS occurred_at, 'DAY'::text AS precision, 'Terminou de ler' AS action,
                       p.name AS title, concat_ws(' · ', i.number, i.cover_date) AS subtitle, '/magazines/' || i.id AS href, i.cover_url AS image_url,
                       '[]'::jsonb AS related_items
                FROM magazine_reads r JOIN magazine_issues i ON i.id = r.issue_id JOIN magazine_publications p ON p.id = i.publication_id
                WHERE r.finished_at IS NOT NULL
                UNION ALL
                SELECT 'game-start:' || r.id AS id, 'GAMES'::text AS domain, 'ACTIVITY'::text AS kind,
                       r.started_at AS occurred_at, 'INSTANT'::text AS precision, 'Começou a jogar' AS action,
                       g.title AS title, NULL::text AS subtitle, '/games/' || COALESCE(g.slug, g.id::text) AS href, g.cover_url AS image_url,
                       '[]'::jsonb AS related_items
                FROM game_sessions r JOIN games g ON g.id = r.game_id
                WHERE r.started_at IS NOT NULL AND r.status <> 'BACKLOG'
                UNION ALL
                SELECT 'game-end:' || r.id AS id, 'GAMES'::text AS domain, 'ACTIVITY'::text AS kind,
                       r.ended_at AS occurred_at, 'INSTANT'::text AS precision, CASE WHEN r.status = 'COMPLETED' THEN 'Concluiu o jogo' WHEN r.status = 'ABANDONED' THEN 'Abandonou o jogo' ELSE 'Encerrou a sessão' END AS action,
                       g.title AS title, NULL::text AS subtitle, '/games/' || COALESCE(g.slug, g.id::text) AS href, g.cover_url AS image_url,
                       '[]'::jsonb AS related_items
                FROM game_sessions r JOIN games g ON g.id = r.game_id
                WHERE r.ended_at IS NOT NULL AND r.status <> 'BACKLOG'
                UNION ALL
                SELECT 'artwork-experience:' || e.id AS id, 'ARTWORKS'::text AS domain, 'ACTIVITY'::text AS kind,
                       e.experienced_at::timestamp AT TIME ZONE :zone AS occurred_at, 'DAY'::text AS precision, CASE WHEN e.experience_type = 'IN_PERSON' THEN 'Contemplou presencialmente' ELSE 'Contemplou' END AS action,
                       a.title AS title, ar.name AS subtitle, '/artworks/' || a.id AS href, (SELECT url FROM artwork_images WHERE artwork_id = a.id ORDER BY is_primary DESC, id LIMIT 1) AS image_url,
                       '[]'::jsonb AS related_items
                FROM artwork_experiences e JOIN artworks a ON a.id = e.artwork_id LEFT JOIN artwork_artists ar ON ar.id = a.artist_id
                WHERE e.experienced_at IS NOT NULL
                UNION ALL
                SELECT 'workout:' || w.id AS id, 'WORKOUTS'::text AS domain, 'ACTIVITY'::text AS kind,
                       w.started_at AS occurred_at, 'INSTANT'::text AS precision, 'Treinou' AS action,
                       CASE w.category WHEN 'RUNNING' THEN 'Corrida' WHEN 'JUMP_ROPE' THEN 'Corda' ELSE 'Academia' END AS title, concat_ws(' · ', w.duration_minutes || ' min', CASE WHEN w.distance_km IS NOT NULL THEN w.distance_km || ' km' END, w.location) AS subtitle, '/workouts' AS href, w.photo_url AS image_url,
                       '[]'::jsonb AS related_items
                FROM workouts w
                WHERE true
                UNION ALL
                SELECT 'album-addition:' || a.id AS id, 'MUSIC'::text AS domain, 'ADDITION'::text AS kind,
                       a.created_at AS occurred_at, 'INSTANT'::text AS precision, 'Incluído no arquivo local' AS action,
                       a.title AS title, NULL::text AS subtitle, '/music/albums/' || a.id AS href, a.cover_url AS image_url,
                       '[]'::jsonb AS related_items
                FROM albums a
                WHERE true
                UNION ALL
                SELECT 'book-addition:' || a.id AS id, 'BOOKS'::text AS domain, 'ADDITION'::text AS kind,
                       a.created_at AS occurred_at, 'INSTANT'::text AS precision, 'Incluído no arquivo local' AS action,
                       a.title AS title, NULL::text AS subtitle, '/books/' || COALESCE(a.slug, a.id::text) AS href, a.cover_url AS image_url,
                       '[]'::jsonb AS related_items
                FROM books a
                WHERE true
                UNION ALL
                SELECT 'movie-addition:' || a.id AS id, 'MOVIES'::text AS domain, 'ADDITION'::text AS kind,
                       a.created_at AS occurred_at, 'INSTANT'::text AS precision, 'Incluído no arquivo local' AS action,
                       COALESCE((SELECT title FROM movie_titles WHERE movie_id = a.id ORDER BY is_primary DESC, id LIMIT 1), a.original_title) AS title, NULL::text AS subtitle, '/movies/' || COALESCE(a.slug, a.id::text) AS href, a.cover_url AS image_url,
                       '[]'::jsonb AS related_items
                FROM movies a
                WHERE true
                UNION ALL
                SELECT 'show-addition:' || a.id AS id, 'SHOWS'::text AS domain, 'ADDITION'::text AS kind,
                       a.created_at AS occurred_at, 'INSTANT'::text AS precision, 'Incluído no arquivo local' AS action,
                       COALESCE((SELECT title FROM tv_show_titles WHERE show_id = a.id ORDER BY is_primary DESC, id LIMIT 1), a.original_title) AS title, NULL::text AS subtitle, '/shows/' || COALESCE(a.slug, a.id::text) AS href, a.cover_url AS image_url,
                       '[]'::jsonb AS related_items
                FROM tv_shows a
                WHERE true
                UNION ALL
                SELECT 'game-addition:' || a.id AS id, 'GAMES'::text AS domain, 'ADDITION'::text AS kind,
                       a.created_at AS occurred_at, 'INSTANT'::text AS precision, 'Incluído no arquivo local' AS action,
                       a.title AS title, NULL::text AS subtitle, '/games/' || COALESCE(a.slug, a.id::text) AS href, a.cover_url AS image_url,
                       '[]'::jsonb AS related_items
                FROM games a
                WHERE true
                UNION ALL
                SELECT 'artwork-addition:' || a.id AS id, 'ARTWORKS'::text AS domain, 'ADDITION'::text AS kind,
                       a.created_at AS occurred_at, 'INSTANT'::text AS precision, 'Incluído no arquivo local' AS action,
                       a.title AS title, NULL::text AS subtitle, '/artworks/' || a.id AS href, (SELECT url FROM artwork_images WHERE artwork_id = a.id ORDER BY is_primary DESC, id LIMIT 1) AS image_url,
                       '[]'::jsonb AS related_items
                FROM artworks a
                WHERE true
            )
            """.trimIndent()
    }
}
