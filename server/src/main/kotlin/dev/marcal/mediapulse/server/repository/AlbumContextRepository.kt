package dev.marcal.mediapulse.server.repository

import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.api.music.AlbumContextDto
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class AlbumContextRepository(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) {
    fun find(albumId: Long): AlbumContextDto? =
        jdbc
            .query(
                "SELECT snapshot::text FROM album_context WHERE album_id = ?",
                { rs, _ -> mapper.readValue(rs.getString(1), AlbumContextDto::class.java) },
                albumId,
            ).firstOrNull()

    fun save(
        albumId: Long,
        context: AlbumContextDto,
    ) {
        jdbc.update(
            """INSERT INTO album_context(album_id, snapshot) VALUES (?, CAST(? AS jsonb))
               ON CONFLICT (album_id) DO UPDATE SET snapshot = EXCLUDED.snapshot""",
            albumId,
            mapper.writeValueAsString(context),
        )
    }

    fun lockAlbum(albumId: Long): String? =
        jdbc
            .query(
                "SELECT musicbrainz_release_group_id FROM albums WHERE id = ? FOR UPDATE",
                { rs, _ -> rs.getString(1) },
                albumId,
            ).firstOrNull()
}
