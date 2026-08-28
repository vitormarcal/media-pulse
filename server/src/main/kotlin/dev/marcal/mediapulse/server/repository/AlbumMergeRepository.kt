package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.music.AlbumMergeCandidateResponse
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant

@Repository
class AlbumMergeRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    data class SuggestionRow(
        val leftAlbumId: Long,
        val rightAlbumId: Long,
        val titleMatch: Boolean,
        val sharedTracks: Long,
        val smallerTrackCount: Long,
    )

    data class MergeStats(
        val movedPlaybacks: Int,
        val migratedTrackLinks: Int,
        val linkedExternalIdentifiers: Int,
        val storedTitleAliases: Int,
    )

    data class TrackLink(
        val albumId: Long,
        val trackId: Long,
        val discNumber: Int?,
        val trackNumber: Int?,
        val createdAt: Instant,
    )

    fun findCatalogAlbumIds(
        query: String,
        limit: Int,
    ): List<Long> =
        jdbc.queryForList(
            """
            SELECT a.id
            FROM albums a
            JOIN artists ar ON ar.id = a.artist_id
            WHERE ar.name ILIKE :pattern OR a.title ILIKE :pattern
            ORDER BY ar.name, a.year NULLS LAST, a.title, a.id
            LIMIT :limit
            """.trimIndent(),
            mapOf("pattern" to "%${query.trim()}%", "limit" to limit.coerceIn(1, 200)),
            Long::class.java,
        )

    fun findSuggestions(
        limit: Int,
        artistQuery: String?,
        albumQuery: String?,
    ): List<SuggestionRow> {
        val artistFilter = if (artistQuery.isNullOrBlank()) "" else "AND ar.name ILIKE :artistPattern"
        val albumFilter = if (albumQuery.isNullOrBlank()) "" else "AND a.title ILIKE :albumPattern"
        val sql =
            """
            WITH album_keys AS (
              SELECT
                a.id,
                a.artist_id,
                regexp_replace(
                  regexp_replace(
                    replace(
                      translate(lower(a.title), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc'),
                      translate(lower(ar.name), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc'), ''
                    ),
                    '(ao vivo|live|deluxe|expanded|remaster(ed)?|anniversary|edition|versao|version)', '', 'g'
                  ),
                  '[^[:alnum:]]+', '', 'g'
                ) AS edition_key,
                COUNT(DISTINCT at.track_id) AS track_count
              FROM albums a
              JOIN artists ar ON ar.id = a.artist_id
              LEFT JOIN album_tracks at ON at.album_id = a.id
              WHERE 1 = 1
                $artistFilter
                $albumFilter
              GROUP BY a.id, ar.name
            ),
            track_keys AS (
              SELECT at.album_id, regexp_replace(translate(lower(t.title), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc'), '[^[:alnum:]]+', '', 'g') AS title_key
              FROM album_tracks at
              JOIN tracks t ON t.id = at.track_id
              GROUP BY at.album_id, regexp_replace(translate(lower(t.title), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc'), '[^[:alnum:]]+', '', 'g')
            )
            SELECT
              l.id AS left_album_id,
              r.id AS right_album_id,
              (l.edition_key <> '' AND l.edition_key = r.edition_key) AS title_match,
              COUNT(DISTINCT CASE WHEN lt.title_key = rt.title_key AND lt.title_key <> '' THEN lt.title_key END) AS shared_tracks,
              LEAST(l.track_count, r.track_count) AS smaller_track_count
            FROM album_keys l
            JOIN album_keys r ON r.artist_id = l.artist_id AND r.id > l.id
            LEFT JOIN track_keys lt ON lt.album_id = l.id
            LEFT JOIN track_keys rt ON rt.album_id = r.id AND rt.title_key = lt.title_key
            GROUP BY l.id, r.id, l.edition_key, r.edition_key, l.track_count, r.track_count
            HAVING (l.edition_key <> '' AND l.edition_key = r.edition_key)
               OR (LEAST(l.track_count, r.track_count) >= 2
                   AND COUNT(DISTINCT CASE WHEN lt.title_key = rt.title_key AND lt.title_key <> '' THEN lt.title_key END) * 1.0
                       / LEAST(l.track_count, r.track_count) >= 0.5)
            ORDER BY title_match DESC, shared_tracks DESC, left_album_id, right_album_id
            LIMIT :limit
            """.trimIndent()
        val params =
            MapSqlParameterSource()
                .addValue("limit", limit.coerceIn(1, 100))
        if (!artistQuery.isNullOrBlank()) params.addValue("artistPattern", "%${artistQuery.trim()}%")
        if (!albumQuery.isNullOrBlank()) params.addValue("albumPattern", "%${albumQuery.trim()}%")
        return jdbc.query(sql, params) { rs, _ ->
            SuggestionRow(
                leftAlbumId = rs.getLong("left_album_id"),
                rightAlbumId = rs.getLong("right_album_id"),
                titleMatch = rs.getBoolean("title_match"),
                sharedTracks = rs.getLong("shared_tracks"),
                smallerTrackCount = rs.getLong("smaller_track_count"),
            )
        }
    }

    fun findCandidates(albumIds: Collection<Long>): List<Pair<Pair<Long, String>, AlbumMergeCandidateResponse>> {
        if (albumIds.isEmpty()) return emptyList()
        val sql =
            """
            SELECT a.id, a.artist_id, ar.name AS artist_name, a.title, a.year, a.cover_url,
                   COUNT(DISTINCT at.track_id) AS track_count,
                   COUNT(DISTINCT tp.id) AS playback_count, MAX(tp.played_at) AS last_played,
                   COALESCE((SELECT string_agg(asi.spotify_id, '|' ORDER BY asi.spotify_id) FROM album_spotify_ids asi WHERE asi.album_id = a.id), '') AS spotify_ids,
                   COALESCE((SELECT string_agg(ami.release_id, '|' ORDER BY ami.release_id) FROM album_musicbrainz_release_ids ami WHERE ami.album_id = a.id), '') AS mb_release_ids,
                   a.musicbrainz_release_group_id,
                   (SELECT mr.rating FROM media_ratings mr WHERE mr.entity_type = 'ALBUM' AND mr.entity_id = a.id) AS rating
            FROM albums a
            JOIN artists ar ON ar.id = a.artist_id
            LEFT JOIN album_tracks at ON at.album_id = a.id
            LEFT JOIN track_playbacks tp ON tp.album_id = a.id
            WHERE a.id IN (:albumIds)
            GROUP BY a.id, ar.name
            """.trimIndent()
        return jdbc.query(sql, mapOf("albumIds" to albumIds)) { rs, _ ->
            val artist = rs.getLong("artist_id") to rs.getString("artist_name")
            artist to
                AlbumMergeCandidateResponse(
                    albumId = rs.getLong("id"),
                    title = rs.getString("title"),
                    year = rs.getObject("year") as Int?,
                    coverUrl = rs.getString("cover_url"),
                    trackCount = rs.getLong("track_count"),
                    playbackCount = rs.getLong("playback_count"),
                    lastPlayed = rs.getTimestamp("last_played")?.toInstant(),
                    spotifyIds = splitIds(rs.getString("spotify_ids")),
                    musicBrainzReleaseIds = splitIds(rs.getString("mb_release_ids")),
                    musicBrainzReleaseGroupId = rs.getString("musicbrainz_release_group_id"),
                    rating = (rs.getObject("rating") as Number?)?.toInt(),
                )
        }
    }

    fun lockAlbums(albumIds: Collection<Long>) {
        albumIds.sorted().forEach { id ->
            jdbc.query("SELECT pg_advisory_xact_lock(:id)", mapOf("id" to id)) { resultSet ->
                resultSet.next()
            }
        }
    }

    fun findTrackLinks(albumIds: Collection<Long>): List<TrackLink> {
        if (albumIds.isEmpty()) return emptyList()
        return jdbc.query(
            """
            SELECT album_id, track_id, disc_number, track_number, created_at
            FROM album_tracks
            WHERE album_id IN (:albumIds)
            ORDER BY album_id, track_id
            """.trimIndent(),
            mapOf("albumIds" to albumIds),
        ) { rs, _ ->
            TrackLink(
                albumId = rs.getLong("album_id"),
                trackId = rs.getLong("track_id"),
                discNumber = rs.getObject("disc_number") as Int?,
                trackNumber = rs.getObject("track_number") as Int?,
                createdAt = rs.getTimestamp("created_at").toInstant(),
            )
        }
    }

    @Suppress("LongMethod")
    fun merge(
        targetId: Long,
        sourceIds: List<Long>,
        artistId: Long,
        ratingAlbumId: Long?,
        trackLinks: List<TrackLink>,
        migratedTrackLinks: Int,
    ): MergeStats {
        val params = MapSqlParameterSource().addValue("targetId", targetId).addValue("sourceIds", sourceIds).addValue("artistId", artistId)

        val aliases =
            jdbc.update(
                """
                INSERT INTO album_title_aliases(album_id, artist_id, title, title_key)
                SELECT :targetId, :artistId, titles.title, titles.title_key
                FROM (
                  SELECT title, title_key FROM albums WHERE id = :targetId OR id IN (:sourceIds)
                  UNION
                  SELECT title, title_key FROM album_title_aliases WHERE album_id IN (:sourceIds)
                ) titles
                ON CONFLICT (artist_id, title_key) DO UPDATE SET album_id = EXCLUDED.album_id, title = EXCLUDED.title
                """.trimIndent(),
                params,
            )

        jdbc.update(
            """
            INSERT INTO album_musicbrainz_release_group_aliases(album_id, release_group_id)
            SELECT :targetId, release_group_id FROM (
              SELECT musicbrainz_release_group_id AS release_group_id FROM albums WHERE id IN (:sourceIds)
              UNION
              SELECT release_group_id FROM album_musicbrainz_release_group_aliases WHERE album_id IN (:sourceIds)
            ) ids WHERE release_group_id IS NOT NULL
            ON CONFLICT (release_group_id) DO UPDATE SET album_id = EXCLUDED.album_id
            """.trimIndent(),
            params,
        )

        val spotify = jdbc.update("UPDATE album_spotify_ids SET album_id = :targetId WHERE album_id IN (:sourceIds)", params)
        val mbReleases = jdbc.update("UPDATE album_musicbrainz_release_ids SET album_id = :targetId WHERE album_id IN (:sourceIds)", params)

        jdbc.update(
            """
            DELETE FROM track_playbacks tp
            USING (
              SELECT id FROM (
                SELECT id, row_number() OVER (PARTITION BY track_id, source, played_at ORDER BY CASE WHEN album_id = :targetId THEN 0 ELSE 1 END, id) rn
                FROM track_playbacks WHERE album_id = :targetId OR album_id IN (:sourceIds)
              ) ranked WHERE rn > 1
            ) duplicates WHERE tp.id = duplicates.id
            """.trimIndent(),
            params,
        )
        val playbacks = jdbc.update("UPDATE track_playbacks SET album_id = :targetId WHERE album_id IN (:sourceIds)", params)

        replaceTrackLinks(targetId, sourceIds, trackLinks)

        copyDistinct("album_genres", "genre_id", params)
        copyDistinct("album_genre_sources", "genre_id, source", params)
        copyDistinct("music_duplicate_review_ignored", "title_key", params)
        copyDistinct("album_term_assignments", "term_id, source, hidden, created_at, updated_at", params, conflict = "(album_id, term_id)")
        mergeLists(params)
        mergeSyncState(params)
        jdbc.update("UPDATE media_comments SET entity_id = :targetId WHERE entity_type = 'ALBUM' AND entity_id IN (:sourceIds)", params)
        mergeRating(targetId, sourceIds, ratingAlbumId)
        jdbc.update("DELETE FROM albums WHERE id IN (:sourceIds)", params)

        return MergeStats(playbacks, migratedTrackLinks, spotify + mbReleases, aliases)
    }

    private fun replaceTrackLinks(
        targetId: Long,
        sourceIds: List<Long>,
        trackLinks: List<TrackLink>,
    ) {
        jdbc.update(
            "DELETE FROM album_tracks WHERE album_id = :targetId OR album_id IN (:sourceIds)",
            mapOf("targetId" to targetId, "sourceIds" to sourceIds),
        )
        if (trackLinks.isEmpty()) return
        jdbc.batchUpdate(
            """
            INSERT INTO album_tracks(album_id, track_id, disc_number, track_number, created_at)
            VALUES (:targetId, :trackId, :discNumber, :trackNumber, :createdAt)
            """.trimIndent(),
            trackLinks
                .map { link ->
                    MapSqlParameterSource()
                        .addValue("targetId", targetId)
                        .addValue("trackId", link.trackId)
                        .addValue("discNumber", link.discNumber)
                        .addValue("trackNumber", link.trackNumber)
                        .addValue("createdAt", Timestamp.from(link.createdAt))
                }.toTypedArray(),
        )
    }

    private fun copyDistinct(
        table: String,
        columns: String,
        params: MapSqlParameterSource,
        conflict: String = "",
    ) {
        val targetColumns = columns.split(",").joinToString(",") { it.trim() }
        val conflictClause = if (conflict.isBlank()) "ON CONFLICT DO NOTHING" else "ON CONFLICT $conflict DO NOTHING"
        jdbc.update(
            "INSERT INTO $table(album_id, $targetColumns) SELECT :targetId, $targetColumns FROM $table WHERE album_id IN (:sourceIds) $conflictClause",
            params,
        )
    }

    private fun mergeLists(params: MapSqlParameterSource) {
        jdbc.update(
            """
            INSERT INTO album_list_items(list_id, album_id, position, listened_at, created_at, updated_at)
            SELECT DISTINCT ON (li.list_id) li.list_id, :targetId, li.position, li.listened_at, li.created_at, li.updated_at
            FROM album_list_items li
            LEFT JOIN album_list_items target ON target.list_id = li.list_id AND target.album_id = :targetId
            WHERE li.album_id IN (:sourceIds) AND target.id IS NULL
            ORDER BY li.list_id, li.position, li.id
            ON CONFLICT (list_id, album_id) DO NOTHING
            """.trimIndent(),
            params,
        )
        jdbc.update("DELETE FROM album_list_items WHERE album_id IN (:sourceIds)", params)
    }

    private fun mergeSyncState(params: MapSqlParameterSource) {
        jdbc.update(
            """
            INSERT INTO album_genre_sync_state(album_id, source, status, last_sync_at, last_note, force_next)
            SELECT DISTINCT ON (source) :targetId, source, status, last_sync_at, last_note, force_next
            FROM album_genre_sync_state WHERE album_id IN (:sourceIds)
            ORDER BY source, last_sync_at DESC NULLS LAST
            ON CONFLICT (album_id, source) DO NOTHING
            """.trimIndent(),
            params,
        )
    }

    private fun mergeRating(
        targetId: Long,
        sourceIds: List<Long>,
        ratingAlbumId: Long?,
    ) {
        val allIds = sourceIds + targetId
        val rating =
            ratingAlbumId?.let {
                jdbc
                    .queryForList(
                        "SELECT rating FROM media_ratings WHERE entity_type = 'ALBUM' AND entity_id = :id",
                        mapOf(
                            "id" to it,
                        ),
                        Int::class.java,
                    ).firstOrNull()
            }
        jdbc.update("DELETE FROM media_ratings WHERE entity_type = 'ALBUM' AND entity_id IN (:ids)", mapOf("ids" to allIds))
        if (rating != null) {
            jdbc.update(
                "INSERT INTO media_ratings(entity_type, entity_id, rating) VALUES ('ALBUM', :id, :rating)",
                mapOf(
                    "id" to targetId,
                    "rating" to rating,
                ),
            )
        }
    }

    private fun splitIds(raw: String?): List<String> = raw?.split('|')?.filter(String::isNotBlank).orEmpty()
}
