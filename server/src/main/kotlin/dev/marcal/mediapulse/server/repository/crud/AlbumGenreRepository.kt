package dev.marcal.mediapulse.server.repository.crud

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class AlbumGenreRepository(
    private val jdbc: JdbcTemplate,
) {
    fun findGenreIdsByAlbum(albumId: Long): Set<Long> =
        jdbc
            .queryForList(
                "SELECT genre_id FROM album_genres WHERE album_id = ?",
                Long::class.java,
                albumId,
            ).toSet()

    fun insert(
        albumId: Long,
        genreIds: List<Long>,
    ) {
        if (genreIds.isEmpty()) return

        jdbc.batchUpdate(
            "INSERT INTO album_genres (album_id, genre_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
            genreIds.map { arrayOf(albumId, it) },
        )
    }
}
