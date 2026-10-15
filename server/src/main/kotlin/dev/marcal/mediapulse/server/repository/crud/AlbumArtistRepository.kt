package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.AlbumArtistCredit
import dev.marcal.mediapulse.server.model.music.AlbumArtistSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class AlbumArtistRepository(
    private val jdbc: JdbcTemplate,
) {
    fun initializeLegacy(albumId: Long) {
        jdbc.update(
            """INSERT INTO album_artists(album_id, position, artist_id, credited_name, join_phrase, source)
               SELECT al.id, 0, a.id, a.name, '', 'LEGACY'
               FROM albums al JOIN artists a ON a.id = al.artist_id
               WHERE al.id = ? AND NOT EXISTS (SELECT 1 FROM album_artists WHERE album_id = al.id)
               ON CONFLICT DO NOTHING""",
            albumId,
        )
    }

    fun lockAlbum(albumId: Long) {
        jdbc.queryForObject("SELECT id FROM albums WHERE id = ? FOR UPDATE", Long::class.java, albumId)
    }

    fun findByAlbumId(albumId: Long): List<AlbumArtistCredit> =
        jdbc.query(
            "SELECT * FROM album_artists WHERE album_id = ? ORDER BY position",
            { rs, _ ->
                AlbumArtistCredit(
                    albumId,
                    rs.getInt("position"),
                    rs.getLong("artist_id"),
                    rs.getString("credited_name"),
                    rs.getString("join_phrase"),
                    AlbumArtistSource.valueOf(rs.getString("source")),
                )
            },
            albumId,
        )

    fun replace(
        albumId: Long,
        credits: List<AlbumArtistCredit>,
    ) {
        jdbc.update("DELETE FROM album_artists WHERE album_id = ?", albumId)
        credits.forEach { credit ->
            jdbc.update(
                """INSERT INTO album_artists(album_id, position, artist_id, credited_name, join_phrase, source)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                albumId,
                credit.position,
                credit.artistId,
                credit.creditedName,
                credit.joinPhrase,
                credit.source.name,
            )
        }
    }
}
