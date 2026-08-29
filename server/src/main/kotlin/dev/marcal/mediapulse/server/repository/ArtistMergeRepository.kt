package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.music.ArtistMergeCandidateResponse
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ArtistMergeRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    data class SuggestionRow(
        val leftId: Long,
        val rightId: Long,
    )

    data class MergeStats(
        val movedAlbums: Int,
        val movedTracks: Int,
        val movedComments: Int,
        val mergedGenres: Int,
        val storedAliases: Int,
    )

    fun findSuggestions(
        limit: Int,
        query: String?,
    ): List<SuggestionRow> {
        val filter = if (query.isNullOrBlank()) "" else "AND (l.name ILIKE :pattern OR r.name ILIKE :pattern)"
        val params = MapSqlParameterSource("limit", limit.coerceIn(1, 100))
        if (!query.isNullOrBlank()) params.addValue("pattern", "%${query.trim()}%")
        return jdbc.query(
            """
            SELECT l.id left_id, r.id right_id,
              l.name left_name, r.name right_name
            FROM artists l JOIN artists r ON r.id > l.id
            WHERE regexp_replace(translate(lower(l.name), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc'), '[^[:alnum:]]+', '', 'g') =
                  regexp_replace(translate(lower(r.name), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc'), '[^[:alnum:]]+', '', 'g')
              $filter
            ORDER BY l.id, r.id LIMIT :limit
            """.trimIndent(),
            params,
        ) { rs, _ ->
            SuggestionRow(
                rs.getLong("left_id"),
                rs.getLong("right_id"),
            )
        }
    }

    fun findCatalogIds(
        query: String,
        limit: Int,
    ): List<Long> =
        jdbc.queryForList(
            "SELECT id FROM artists WHERE name ILIKE :pattern ORDER BY name, id LIMIT :limit",
            mapOf("pattern" to "%${query.trim()}%", "limit" to limit.coerceIn(1, 200)),
            Long::class.java,
        )

    fun findCandidates(ids: Collection<Long>): List<ArtistMergeCandidateResponse> {
        if (ids.isEmpty()) return emptyList()
        return jdbc.query(
            """
            SELECT a.id, a.name, a.profile_image_url, a.spotify_id, a.musicbrainz_artist_id,
                   a.artist_type, a.area_name, a.disambiguation,
                   (SELECT COUNT(*) FROM albums al WHERE al.artist_id=a.id) album_count,
                   (SELECT COUNT(*) FROM tracks t WHERE t.artist_id=a.id) track_count,
                   (SELECT COUNT(*) FROM track_playbacks tp JOIN tracks t ON t.id=tp.track_id WHERE t.artist_id=a.id) playback_count,
                   (SELECT MAX(tp.played_at) FROM track_playbacks tp JOIN tracks t ON t.id=tp.track_id WHERE t.artist_id=a.id) last_played,
                   (SELECT rating FROM media_ratings WHERE entity_type='ARTIST' AND entity_id=a.id) rating,
                   COALESCE((SELECT string_agg(name, '|' ORDER BY name) FROM artist_aliases aa WHERE aa.artist_id=a.id), '') aliases
            FROM artists a WHERE a.id IN (:ids) ORDER BY a.name, a.id
            """.trimIndent(),
            mapOf("ids" to ids),
        ) { rs, _ ->
            ArtistMergeCandidateResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("profile_image_url"),
                rs.getString("spotify_id"),
                rs.getString("musicbrainz_artist_id"),
                rs.getString("artist_type"),
                rs.getString("area_name"),
                rs.getString("disambiguation"),
                rs.getLong("album_count"),
                rs.getLong("track_count"),
                rs.getLong("playback_count"),
                rs.getTimestamp("last_played")?.toInstant(),
                (rs.getObject("rating") as Number?)?.toInt(),
                rs.getString("aliases").split('|').filter(String::isNotBlank),
            )
        }
    }

    fun lockArtists(ids: Collection<Long>) =
        ids.sorted().forEach { id ->
            jdbc.query("SELECT pg_advisory_xact_lock(:id)", mapOf("id" to id)) { rs -> rs.next() }
        }

    @Suppress("LongMethod")
    fun merge(request: MergeCommand): MergeStats {
        val p =
            MapSqlParameterSource()
                .addValue("targetId", request.targetId)
                .addValue("sourceIds", request.sourceIds)
                .addValue("nameId", request.nameId)
                .addValue("imageId", request.imageId)
                .addValue("mbId", request.musicBrainzId)
                .addValue("spotifyId", request.spotifyId)
                .addValue("musicBrainzValue", request.musicBrainzValue)
                .addValue("fingerprint", request.fingerprint)
        val inheritedAliases =
            jdbc.update(
                """
                INSERT INTO artist_name_aliases(artist_id, name, name_key, created_at)
                SELECT :targetId, name, name_key, created_at
                FROM artist_name_aliases WHERE artist_id IN (:sourceIds)
                ON CONFLICT (name_key) DO UPDATE SET artist_id=EXCLUDED.artist_id, name=EXCLUDED.name
                """.trimIndent(),
                p,
            )
        val selectedAliases =
            if (request.aliasIds.isEmpty()) {
                0
            } else {
                jdbc.update(
                    """
                    INSERT INTO artist_name_aliases(artist_id, name, name_key)
                    SELECT :targetId, name, fingerprint FROM artists WHERE id IN (:aliasIds)
                    ON CONFLICT (name_key) DO UPDATE SET artist_id=EXCLUDED.artist_id, name=EXCLUDED.name
                    """.trimIndent(),
                    p.addValue("aliasIds", request.aliasIds),
                )
            }
        jdbc.update("UPDATE artists SET spotify_id=NULL, musicbrainz_artist_id=NULL WHERE id IN (:sourceIds)", p)
        jdbc.update(
            """
            UPDATE artists target SET
              name=(SELECT name FROM artists WHERE id=:nameId),
              profile_image_url=(SELECT profile_image_url FROM artists WHERE id=:imageId),
              wikidata_entity_id=(SELECT wikidata_entity_id FROM artists WHERE id=:imageId),
              wikimedia_file_name=(SELECT wikimedia_file_name FROM artists WHERE id=:imageId),
              wikimedia_original_url=(SELECT wikimedia_original_url FROM artists WHERE id=:imageId),
              wikimedia_description_url=(SELECT wikimedia_description_url FROM artists WHERE id=:imageId),
              wikimedia_author=(SELECT wikimedia_author FROM artists WHERE id=:imageId),
              wikimedia_license=(SELECT wikimedia_license FROM artists WHERE id=:imageId),
              wikimedia_license_url=(SELECT wikimedia_license_url FROM artists WHERE id=:imageId),
              wikimedia_imported_at=(SELECT wikimedia_imported_at FROM artists WHERE id=:imageId),
              wikimedia_attempted_at=(SELECT wikimedia_attempted_at FROM artists WHERE id=:imageId),
              wikimedia_sync_error=(SELECT wikimedia_sync_error FROM artists WHERE id=:imageId),
              musicbrainz_artist_id=NULL, spotify_id=NULL, updated_at=NOW()
            WHERE target.id=:targetId
            """.trimIndent(),
            p,
        )
        jdbc.update(
            """
            UPDATE artists target SET
              musicbrainz_artist_id=:musicBrainzValue, spotify_id=:spotifyId,
              artist_type=(SELECT artist_type FROM artists WHERE id=:mbId), country_code=(SELECT country_code FROM artists WHERE id=:mbId),
              area_name=(SELECT area_name FROM artists WHERE id=:mbId), begin_area_name=(SELECT begin_area_name FROM artists WHERE id=:mbId),
              life_span_begin=(SELECT life_span_begin FROM artists WHERE id=:mbId), life_span_end=(SELECT life_span_end FROM artists WHERE id=:mbId),
              life_span_ended=(SELECT life_span_ended FROM artists WHERE id=:mbId), disambiguation=(SELECT disambiguation FROM artists WHERE id=:mbId),
              musicbrainz_synced_at=(SELECT musicbrainz_synced_at FROM artists WHERE id=:mbId),
              musicbrainz_sync_error=(SELECT musicbrainz_sync_error FROM artists WHERE id=:mbId)
            WHERE target.id=:targetId
            """.trimIndent(),
            p,
        )
        val albums = jdbc.update("UPDATE albums SET artist_id=:targetId WHERE artist_id IN (:sourceIds)", p)
        jdbc.update(
            """
            DELETE FROM album_title_aliases source
            WHERE source.artist_id IN (:sourceIds)
              AND EXISTS (
                SELECT 1 FROM album_title_aliases winner
                WHERE winner.title_key=source.title_key
                  AND (winner.artist_id=:targetId OR winner.artist_id IN (:sourceIds))
                  AND (winner.artist_id=:targetId OR winner.id < source.id)
              )
            """.trimIndent(),
            p,
        )
        jdbc.update("UPDATE album_title_aliases SET artist_id=:targetId WHERE artist_id IN (:sourceIds)", p)
        val tracks = jdbc.update("UPDATE tracks SET artist_id=:targetId WHERE artist_id IN (:sourceIds)", p)
        val genres =
            jdbc.update(
                "INSERT INTO artist_term_assignments(artist_id, term_id, source, hidden, created_at, updated_at) SELECT :targetId, term_id, source, hidden, created_at, updated_at FROM artist_term_assignments WHERE artist_id IN (:sourceIds) ON CONFLICT (artist_id, term_id) DO NOTHING",
                p,
            )
        if (request.musicBrainzId != request.targetId) {
            jdbc.update("DELETE FROM artist_aliases WHERE artist_id=:targetId", p)
            jdbc.update(
                "INSERT INTO artist_aliases(artist_id,name,locale,sort_name,alias_type,is_primary,created_at) SELECT :targetId,name,locale,sort_name,alias_type,is_primary,created_at FROM artist_aliases WHERE artist_id=:mbId ON CONFLICT DO NOTHING",
                p,
            )
            jdbc.update("DELETE FROM artist_external_links WHERE artist_id=:targetId", p)
            jdbc.update(
                "INSERT INTO artist_external_links(artist_id,link_type,url,created_at) SELECT :targetId,link_type,url,created_at FROM artist_external_links WHERE artist_id=:mbId ON CONFLICT DO NOTHING",
                p,
            )
        }
        val comments =
            jdbc.update(
                "UPDATE media_comments SET entity_id=:targetId WHERE entity_type='ARTIST' AND entity_id IN (:sourceIds)",
                p,
            )
        mergeRating(request.targetId, request.sourceIds, request.ratingId)
        jdbc.update("DELETE FROM artists WHERE id IN (:sourceIds)", p)
        jdbc.update("UPDATE artists SET fingerprint=:fingerprint WHERE id=:targetId", p)
        return MergeStats(albums, tracks, comments, genres, inheritedAliases + selectedAliases)
    }

    private fun mergeRating(
        targetId: Long,
        sourceIds: List<Long>,
        ratingId: Long?,
    ) {
        val ids = sourceIds + targetId
        val rating =
            ratingId?.let {
                jdbc
                    .queryForList(
                        "SELECT rating FROM media_ratings WHERE entity_type='ARTIST' AND entity_id=:id",
                        mapOf(
                            "id" to it,
                        ),
                        Int::class.java,
                    ).firstOrNull()
            }
        jdbc.update("DELETE FROM media_ratings WHERE entity_type='ARTIST' AND entity_id IN (:ids)", mapOf("ids" to ids))
        if (rating !=
            null
        ) {
            jdbc.update(
                "INSERT INTO media_ratings(entity_type,entity_id,rating) VALUES ('ARTIST',:id,:rating)",
                mapOf(
                    "id" to targetId,
                    "rating" to rating,
                ),
            )
        }
    }

    data class MergeCommand(
        val targetId: Long,
        val sourceIds: List<Long>,
        val nameId: Long,
        val imageId: Long,
        val musicBrainzId: Long,
        val ratingId: Long?,
        val aliasIds: List<Long>,
        val spotifyId: String?,
        val musicBrainzValue: String?,
        val fingerprint: String,
    )
}
