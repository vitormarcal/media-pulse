package dev.marcal.mediapulse.server.repository.query

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class AlbumQueryRepository(
    private val jdbc: JdbcTemplate,
) {
    fun findAlbumIdsForGenreEnrichment(
        limit: Int,
        onlyMissingAlbumGenres: Boolean,
    ): List<Long> {
        val sql =
            if (onlyMissingAlbumGenres) {
                """
                SELECT a.id
                FROM albums a
                LEFT JOIN album_genres ag ON ag.album_id = a.id
                WHERE ag.album_id IS NULL
                ORDER BY a.id
                LIMIT ?
                """.trimIndent()
            } else {
                """
                SELECT a.id
                FROM albums a
                ORDER BY a.id
                LIMIT ?
                """.trimIndent()
            }

        return jdbc.queryForList(sql, Long::class.java, limit)
    }
}
