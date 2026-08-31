package dev.marcal.mediapulse.server.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class PersonProfileRepository(
    private val jdbc: JdbcTemplate,
) {
    fun findPendingPersonIds(limit: Int): List<Long> =
        jdbc.queryForList(
            """
            SELECT id
            FROM people
            WHERE tmdb_synced_at IS NULL
              AND (
                tmdb_sync_attempted_at IS NULL
                OR tmdb_sync_attempted_at <= NOW() - INTERVAL '1 day'
              )
            ORDER BY tmdb_sync_attempted_at NULLS FIRST, id
            LIMIT ?
            """.trimIndent(),
            Long::class.java,
            limit,
        )

    fun replaceAliases(
        personId: Long,
        aliases: List<String>,
    ) {
        jdbc.update("DELETE FROM person_aliases WHERE person_id = ?", personId)
        aliases
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)
            .forEach { alias ->
                jdbc.update(
                    "INSERT INTO person_aliases(person_id, name) VALUES (?, ?)",
                    personId,
                    alias,
                )
            }
    }
}
