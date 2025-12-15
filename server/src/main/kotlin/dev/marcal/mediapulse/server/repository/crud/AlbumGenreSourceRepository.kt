package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.GenreSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class AlbumGenreSourceRepository(
    private val jdbc: JdbcTemplate,
) {
    fun insert(
        albumId: Long,
        genreIds: List<Long>,
        source: GenreSource,
    ) {
        if (genreIds.isEmpty()) return

        jdbc.batchUpdate(
            """
            INSERT INTO album_genre_sources (album_id, genre_id, source)
            VALUES (?, ?, ?)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
            genreIds.map { arrayOf(albumId, it, source.name) },
        )
    }
}
