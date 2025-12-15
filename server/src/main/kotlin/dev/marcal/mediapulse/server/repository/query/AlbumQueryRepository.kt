package dev.marcal.mediapulse.server.repository.query

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class AlbumQueryRepository(
    private val jdbc: JdbcTemplate,
) {
    fun findAlbumIdsPending(
        limit: Int,
        source: String,
    ): List<Long> =
        jdbc.queryForList(
            """
            SELECT a.id
            FROM albums a
            LEFT JOIN album_genre_sync_state s
              ON s.album_id = a.id AND s.source = ?
            WHERE COALESCE(s.status, 'NEVER') <> 'DONE'
               OR COALESCE(s.force_next, FALSE) = TRUE
            ORDER BY a.id
            LIMIT ?
            """.trimIndent(),
            Long::class.java,
            source,
            limit,
        )
}
