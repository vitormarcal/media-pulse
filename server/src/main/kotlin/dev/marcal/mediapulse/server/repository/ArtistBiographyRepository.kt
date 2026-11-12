package dev.marcal.mediapulse.server.repository

import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.api.music.ArtistBiographyDto
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ArtistBiographyRepository(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) {
    fun find(artistId: Long): ArtistBiographyDto? =
        jdbc
            .query(
                "SELECT snapshot::text FROM artist_biographies WHERE artist_id = ?",
                { rs, _ -> mapper.readValue(rs.getString(1), ArtistBiographyDto::class.java) },
                artistId,
            ).firstOrNull()

    fun current(artistId: Long): ArtistBiographyDto? =
        jdbc
            .query(
                """SELECT b.snapshot::text FROM artist_biographies b JOIN artists a ON a.id = b.artist_id
           WHERE b.artist_id = ? AND b.snapshot->>'artistMbid' = a.musicbrainz_artist_id""",
                { rs, _ -> mapper.readValue(rs.getString(1), ArtistBiographyDto::class.java) },
                artistId,
            ).firstOrNull()

    fun save(
        artistId: Long,
        biography: ArtistBiographyDto,
    ) {
        jdbc.update(
            """INSERT INTO artist_biographies(artist_id, snapshot) VALUES (?, CAST(? AS jsonb))
            ON CONFLICT (artist_id) DO UPDATE SET snapshot = EXCLUDED.snapshot""",
            artistId,
            mapper.writeValueAsString(biography),
        )
    }

    fun lockArtist(artistId: Long): String? =
        jdbc
            .query(
                "SELECT musicbrainz_artist_id FROM artists WHERE id = ? FOR UPDATE",
                { rs, _ -> rs.getString(1) },
                artistId,
            ).firstOrNull()
}
