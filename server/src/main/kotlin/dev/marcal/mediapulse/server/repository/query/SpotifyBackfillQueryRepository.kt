package dev.marcal.mediapulse.server.repository.query

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class SpotifyBackfillQueryRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    data class AlbumToBackfill(
        val albumId: Long,
        val spotifyAlbumId: String,
        val withoutPosition: Int,
        val withPosition: Int,
    )

    fun findAlbumsToBackfill(limit: Int): List<AlbumToBackfill> {
        val sql =
            """
            SELECT
              al.id AS album_id,
              ei.external_id AS spotify_album_id,
              count(*) FILTER (WHERE at.disc_number IS NULL OR at.track_number IS NULL) AS without_position,
              count(*) FILTER (WHERE at.disc_number IS NOT NULL AND at.track_number IS NOT NULL) AS with_position
            FROM album_tracks at
            JOIN albums al ON al.id = at.album_id
            JOIN external_identifiers ei
              ON ei.entity_type = 'ALBUM'
             AND ei.entity_id = al.id
             AND ei.provider = 'SPOTIFY'
            GROUP BY al.id, ei.external_id
            HAVING count(*) FILTER (WHERE at.disc_number IS NULL OR at.track_number IS NULL) > 0
            ORDER BY without_position DESC, with_position ASC, al.id DESC
            LIMIT :limit
            """.trimIndent()

        return jdbc.query(
            sql,
            mapOf("limit" to limit),
        ) { rs, _ ->
            AlbumToBackfill(
                albumId = rs.getLong("album_id"),
                spotifyAlbumId = rs.getString("spotify_album_id"),
                withoutPosition = rs.getInt("without_position"),
                withPosition = rs.getInt("with_position"),
            )
        }
    }
}
