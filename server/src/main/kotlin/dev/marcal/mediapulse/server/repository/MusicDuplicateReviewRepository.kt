package dev.marcal.mediapulse.server.repository

import jakarta.persistence.EntityManager
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class MusicDuplicateReviewRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val entityManager: EntityManager,
) {
    data class DuplicateTrackCandidateRow(
        val albumId: Long,
        val albumTitle: String,
        val albumYear: Int?,
        val albumCoverUrl: String?,
        val artistId: Long,
        val artistName: String,
        val groupKey: String,
        val normalizedTitle: String,
        val ignored: Boolean,
        val trackId: Long,
        val title: String,
        val durationMs: Int?,
        val discNumber: Int?,
        val trackNumber: Int?,
        val playbackCount: Long,
        val lastPlayed: Instant?,
        val hasMusicBrainz: Boolean,
        val hasSpotify: Boolean,
        val externalIdentifiers: List<String>,
    )

    data class MergeStats(
        val deletedDuplicatePlaybacks: Int,
        val movedPlaybacks: Int,
        val linkedExternalIdentifiers: Int,
        val migratedAlbumLinks: Int,
    )

    fun findDuplicateTrackCandidates(
        limit: Int,
        cursorAlbumId: Long?,
        cursorGroupKey: String?,
        includeIgnored: Boolean,
        artistQuery: String?,
        albumQuery: String?,
    ): List<DuplicateTrackCandidateRow> {
        val cursorWhereClause =
            if (cursorAlbumId != null && cursorGroupKey != null) {
                """
                AND (
                  cg.album_id > :cursorAlbumId
                  OR (cg.album_id = :cursorAlbumId AND cg.group_key > :cursorGroupKey)
                )
                """.trimIndent()
            } else {
                ""
            }

        val artistWhereClause =
            if (!artistQuery.isNullOrBlank()) {
                "AND ar.name ILIKE :artistPattern"
            } else {
                ""
            }

        val albumWhereClause =
            if (!albumQuery.isNullOrBlank()) {
                "AND al.title ILIKE :albumPattern"
            } else {
                ""
            }

        val sql =
            """
            WITH track_stats AS (
              SELECT
                al.id AS album_id,
                al.title AS album_title,
                al.year AS album_year,
                al.cover_url AS album_cover_url,
                ar.id AS artist_id,
                ar.name AS artist_name,
                regexp_replace(lower(t.title), '[^[:alnum:]]+', '', 'g') AS group_key,
                lower(t.title) AS normalized_title,
                t.id AS track_id,
                t.title,
                t.duration_ms,
                at.disc_number,
                at.track_number,
                COUNT(tp.id) AS playback_count,
                MAX(tp.played_at) AS last_played,
                COALESCE(bool_or(ei.provider = 'MUSICBRAINZ'), false) AS has_musicbrainz,
                COALESCE(bool_or(ei.provider = 'SPOTIFY'), false) AS has_spotify,
                COALESCE(string_agg(DISTINCT ei.provider || ':' || ei.external_id, '|' ORDER BY ei.provider || ':' || ei.external_id), '') AS external_ids
              FROM album_tracks at
              JOIN tracks t ON t.id = at.track_id
              JOIN albums al ON al.id = at.album_id
              JOIN artists ar ON ar.id = al.artist_id
              LEFT JOIN external_identifiers ei
                ON ei.entity_type = 'TRACK'
               AND ei.entity_id = t.id
              LEFT JOIN track_playbacks tp
                ON tp.track_id = t.id
               AND tp.album_id = al.id
              WHERE 1 = 1
              $artistWhereClause
              $albumWhereClause
              GROUP BY
                al.id,
                al.title,
                al.year,
                al.cover_url,
                ar.id,
                ar.name,
                t.id,
                t.title,
                t.duration_ms,
                at.disc_number,
                at.track_number
            ),
            candidate_groups AS (
              SELECT
                ts.album_id,
                ts.group_key
              FROM track_stats ts
              WHERE ts.group_key <> ''
              GROUP BY ts.album_id, ts.group_key
              HAVING COUNT(*) > 1
            ),
            filtered_groups AS (
              SELECT
                cg.album_id,
                cg.group_key
              FROM candidate_groups cg
              LEFT JOIN music_duplicate_review_ignored ign
                ON ign.album_id = cg.album_id
               AND ign.title_key = cg.group_key
              WHERE (:includeIgnored = TRUE OR ign.album_id IS NULL)
              $cursorWhereClause
              ORDER BY cg.album_id ASC, cg.group_key ASC
              LIMIT :limitPlusOne
            )
            SELECT
              ts.album_id,
              ts.album_title,
              ts.album_year,
              ts.album_cover_url,
              ts.artist_id,
              ts.artist_name,
              ts.group_key,
              ts.normalized_title,
              (ign.album_id IS NOT NULL) AS ignored,
              ts.track_id,
              ts.title,
              ts.duration_ms,
              ts.disc_number,
              ts.track_number,
              ts.playback_count,
              ts.last_played,
              ts.has_musicbrainz,
              ts.has_spotify,
              ts.external_ids
            FROM filtered_groups fg
            JOIN track_stats ts
              ON ts.album_id = fg.album_id
             AND ts.group_key = fg.group_key
            LEFT JOIN music_duplicate_review_ignored ign
              ON ign.album_id = ts.album_id
             AND ign.title_key = ts.group_key
            ORDER BY
              ts.album_id ASC,
              ts.group_key ASC,
              ts.has_musicbrainz DESC,
              ts.playback_count DESC,
              ts.track_number ASC NULLS LAST,
              ts.track_id ASC
            """.trimIndent()

        val params =
            MapSqlParameterSource()
                .addValue("includeIgnored", includeIgnored)
                .addValue("limitPlusOne", limit + 1)

        if (cursorAlbumId != null && cursorGroupKey != null) {
            params
                .addValue("cursorAlbumId", cursorAlbumId)
                .addValue("cursorGroupKey", cursorGroupKey)
        }

        if (!artistQuery.isNullOrBlank()) {
            params.addValue("artistPattern", "%${artistQuery.trim()}%")
        }

        if (!albumQuery.isNullOrBlank()) {
            params.addValue("albumPattern", "%${albumQuery.trim()}%")
        }

        return jdbc.query(sql, params) { rs, _ ->
            DuplicateTrackCandidateRow(
                albumId = rs.getLong("album_id"),
                albumTitle = rs.getString("album_title"),
                albumYear = rs.getObject("album_year") as Int?,
                albumCoverUrl = rs.getString("album_cover_url"),
                artistId = rs.getLong("artist_id"),
                artistName = rs.getString("artist_name"),
                groupKey = rs.getString("group_key"),
                normalizedTitle = rs.getString("normalized_title"),
                ignored = rs.getBoolean("ignored"),
                trackId = rs.getLong("track_id"),
                title = rs.getString("title"),
                durationMs = rs.getObject("duration_ms") as Int?,
                discNumber = rs.getObject("disc_number") as Int?,
                trackNumber = rs.getObject("track_number") as Int?,
                playbackCount = rs.getLong("playback_count"),
                lastPlayed = rs.getTimestamp("last_played")?.toInstant(),
                hasMusicBrainz = rs.getBoolean("has_musicbrainz"),
                hasSpotify = rs.getBoolean("has_spotify"),
                externalIdentifiers =
                    rs
                        .getString("external_ids")
                        ?.split("|")
                        ?.filter { it.isNotBlank() }
                        .orEmpty(),
            )
        }
    }

    fun findGroupCandidates(
        albumId: Long,
        groupKey: String,
    ): List<DuplicateTrackCandidateRow> {
        val sql =
            """
            SELECT
              al.id AS album_id,
              al.title AS album_title,
              al.year AS album_year,
              al.cover_url AS album_cover_url,
              ar.id AS artist_id,
              ar.name AS artist_name,
              regexp_replace(lower(t.title), '[^[:alnum:]]+', '', 'g') AS group_key,
              lower(t.title) AS normalized_title,
              (ign.album_id IS NOT NULL) AS ignored,
              t.id AS track_id,
              t.title,
              t.duration_ms,
              at.disc_number,
              at.track_number,
              COUNT(tp.id) AS playback_count,
              MAX(tp.played_at) AS last_played,
              COALESCE(bool_or(ei.provider = 'MUSICBRAINZ'), false) AS has_musicbrainz,
              COALESCE(bool_or(ei.provider = 'SPOTIFY'), false) AS has_spotify,
              COALESCE(string_agg(DISTINCT ei.provider || ':' || ei.external_id, '|' ORDER BY ei.provider || ':' || ei.external_id), '') AS external_ids
            FROM album_tracks at
            JOIN tracks t ON t.id = at.track_id
            JOIN albums al ON al.id = at.album_id
            JOIN artists ar ON ar.id = al.artist_id
            LEFT JOIN external_identifiers ei
              ON ei.entity_type = 'TRACK'
             AND ei.entity_id = t.id
            LEFT JOIN track_playbacks tp
              ON tp.track_id = t.id
             AND tp.album_id = al.id
            LEFT JOIN music_duplicate_review_ignored ign
              ON ign.album_id = al.id
             AND ign.title_key = regexp_replace(lower(t.title), '[^[:alnum:]]+', '', 'g')
            WHERE al.id = :albumId
              AND regexp_replace(lower(t.title), '[^[:alnum:]]+', '', 'g') = :groupKey
            GROUP BY
              al.id,
              al.title,
              al.year,
              al.cover_url,
              ar.id,
              ar.name,
              ign.album_id,
              t.id,
              t.title,
              t.duration_ms,
              at.disc_number,
              at.track_number
            ORDER BY has_musicbrainz DESC, playback_count DESC, track_number ASC NULLS LAST, track_id ASC
            """.trimIndent()

        return jdbc.query(
            sql,
            mapOf("albumId" to albumId, "groupKey" to groupKey),
        ) { rs, _ ->
            DuplicateTrackCandidateRow(
                albumId = rs.getLong("album_id"),
                albumTitle = rs.getString("album_title"),
                albumYear = rs.getObject("album_year") as Int?,
                albumCoverUrl = rs.getString("album_cover_url"),
                artistId = rs.getLong("artist_id"),
                artistName = rs.getString("artist_name"),
                groupKey = rs.getString("group_key"),
                normalizedTitle = rs.getString("normalized_title"),
                ignored = rs.getBoolean("ignored"),
                trackId = rs.getLong("track_id"),
                title = rs.getString("title"),
                durationMs = rs.getObject("duration_ms") as Int?,
                discNumber = rs.getObject("disc_number") as Int?,
                trackNumber = rs.getObject("track_number") as Int?,
                playbackCount = rs.getLong("playback_count"),
                lastPlayed = rs.getTimestamp("last_played")?.toInstant(),
                hasMusicBrainz = rs.getBoolean("has_musicbrainz"),
                hasSpotify = rs.getBoolean("has_spotify"),
                externalIdentifiers =
                    rs
                        .getString("external_ids")
                        ?.split("|")
                        ?.filter { it.isNotBlank() }
                        .orEmpty(),
            )
        }
    }

    fun setIgnored(
        albumId: Long,
        groupKey: String,
        ignored: Boolean,
    ) {
        if (ignored) {
            jdbc.update(
                """
                INSERT INTO music_duplicate_review_ignored(album_id, title_key)
                VALUES (:albumId, :groupKey)
                ON CONFLICT (album_id, title_key) DO NOTHING
                """.trimIndent(),
                mapOf("albumId" to albumId, "groupKey" to groupKey),
            )
        } else {
            jdbc.update(
                """
                DELETE FROM music_duplicate_review_ignored
                WHERE album_id = :albumId
                  AND title_key = :groupKey
                """.trimIndent(),
                mapOf("albumId" to albumId, "groupKey" to groupKey),
            )
        }
    }

    fun clearIgnored(
        albumId: Long,
        groupKey: String,
    ) {
        setIgnored(albumId, groupKey, ignored = false)
    }

    fun lockAlbum(albumId: Long) {
        entityManager
            .createNativeQuery("SELECT pg_advisory_xact_lock(:albumId)")
            .setParameter("albumId", albumId)
            .singleResult
    }

    fun mergeTracks(
        targetTrackId: Long,
        sourceTrackIds: List<Long>,
    ): MergeStats {
        if (sourceTrackIds.isEmpty()) {
            return MergeStats(0, 0, 0, 0)
        }

        val params =
            MapSqlParameterSource()
                .addValue("targetTrackId", targetTrackId)
                .addValue("sourceTrackIds", sourceTrackIds)

        val deletedDuplicatePlaybacks =
            jdbc.update(
                """
                DELETE FROM track_playbacks tp
                USING (
                  SELECT id
                  FROM (
                    SELECT
                      id,
                      row_number() OVER (
                        PARTITION BY album_id, source, played_at
                        ORDER BY CASE WHEN track_id = :targetTrackId THEN 0 ELSE 1 END, id
                      ) AS rn
                    FROM track_playbacks
                    WHERE track_id = :targetTrackId
                       OR track_id IN (:sourceTrackIds)
                  ) ranked
                  WHERE ranked.rn > 1
                ) duplicates
                WHERE tp.id = duplicates.id
                """.trimIndent(),
                params,
            )

        val movedPlaybacks =
            jdbc.update(
                """
                UPDATE track_playbacks
                SET track_id = :targetTrackId
                WHERE track_id IN (:sourceTrackIds)
                """.trimIndent(),
                params,
            )

        val linkedExternalIdentifiers =
            jdbc.update(
                """
                UPDATE external_identifiers
                SET entity_id = :targetTrackId
                WHERE entity_type = 'TRACK'
                  AND entity_id IN (:sourceTrackIds)
                """.trimIndent(),
                params,
            )

        val migratedAlbumLinks =
            jdbc.update(
                """
                INSERT INTO album_tracks(album_id, track_id, disc_number, track_number, created_at)
                SELECT album_id, :targetTrackId, disc_number, track_number, created_at
                FROM album_tracks
                WHERE track_id IN (:sourceTrackIds)
                ON CONFLICT (album_id, track_id)
                DO UPDATE SET
                  disc_number = COALESCE(album_tracks.disc_number, EXCLUDED.disc_number),
                  track_number = COALESCE(album_tracks.track_number, EXCLUDED.track_number)
                """.trimIndent(),
                params,
            )

        jdbc.update(
            """
            DELETE FROM album_tracks
            WHERE track_id IN (:sourceTrackIds)
            """.trimIndent(),
            params,
        )

        jdbc.update(
            """
            DELETE FROM tracks
            WHERE id IN (:sourceTrackIds)
            """.trimIndent(),
            params,
        )

        return MergeStats(
            deletedDuplicatePlaybacks = deletedDuplicatePlaybacks,
            movedPlaybacks = movedPlaybacks,
            linkedExternalIdentifiers = linkedExternalIdentifiers,
            migratedAlbumLinks = migratedAlbumLinks,
        )
    }
}
