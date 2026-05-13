package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.AlbumTermSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class AlbumTermAssignmentRepository(
    private val jdbc: JdbcTemplate,
) {
    fun upsert(
        albumId: Long,
        termId: Long,
        source: AlbumTermSource,
    ) {
        jdbc.update(
            """
            INSERT INTO album_term_assignments (album_id, term_id, source, hidden, updated_at)
            VALUES (?, ?, ?, FALSE, NOW())
            ON CONFLICT (album_id, term_id)
            DO UPDATE SET
              source = EXCLUDED.source,
              hidden = FALSE,
              updated_at = NOW()
            """.trimIndent(),
            albumId,
            termId,
            source.name,
        )
    }

    fun updateVisibility(
        albumId: Long,
        termId: Long,
        hidden: Boolean,
    ): Int =
        jdbc.update(
            """
            UPDATE album_term_assignments
            SET hidden = ?, updated_at = NOW()
            WHERE album_id = ? AND term_id = ?
            """.trimIndent(),
            hidden,
            albumId,
            termId,
        )
}
