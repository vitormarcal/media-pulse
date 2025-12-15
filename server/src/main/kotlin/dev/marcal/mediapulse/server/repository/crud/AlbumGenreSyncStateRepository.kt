package dev.marcal.mediapulse.server.repository.crud

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class AlbumGenreSyncStateRepository(
    private val jdbc: JdbcTemplate,
) {
    fun shouldFetch(
        albumId: Long,
        source: String,
    ): Boolean {
        val rows =
            jdbc.query(
                """
                SELECT status, force_next
                FROM album_genre_sync_state
                WHERE album_id = ? AND source = ?
                """.trimIndent(),
                arrayOf(albumId, source),
            ) { rs, _ -> rs.getString("status") to rs.getBoolean("force_next") }

        val row = rows.firstOrNull() ?: return true
        val (status, forceNext) = row
        return forceNext || status != "DONE"
    }

    fun markDone(
        albumId: Long,
        source: String,
        note: String? = null,
    ) {
        jdbc.update(
            """
            INSERT INTO album_genre_sync_state (album_id, source, status, last_sync_at, last_note, force_next)
            VALUES (?, ?, 'DONE', NOW(), ?, FALSE)
            ON CONFLICT (album_id, source)
            DO UPDATE SET status='DONE', last_sync_at=NOW(), last_note=EXCLUDED.last_note, force_next=FALSE
            """.trimIndent(),
            albumId,
            source,
            note,
        )
    }

    fun markFailed(
        albumId: Long,
        source: String,
        error: String,
        forceNext: Boolean = false,
    ) {
        jdbc.update(
            """
            INSERT INTO album_genre_sync_state (album_id, source, status, last_sync_at, last_note, force_next)
            VALUES (?, ?, 'FAILED', NOW(), ?, ?)
            ON CONFLICT (album_id, source)
            DO UPDATE SET
                status='FAILED',
                last_sync_at=NOW(),
                last_note=EXCLUDED.last_note,
                force_next=EXCLUDED.force_next
            """.trimIndent(),
            albumId,
            source,
            error.take(2000),
            forceNext,
        )
    }
}
