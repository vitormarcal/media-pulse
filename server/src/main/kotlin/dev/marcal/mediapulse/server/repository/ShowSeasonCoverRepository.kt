package dev.marcal.mediapulse.server.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ShowSeasonCoverRepository(
    private val jdbc: JdbcTemplate,
) {
    fun save(
        showId: Long,
        seasonNumber: Int,
        url: String,
    ) {
        jdbc.update(
            """
            INSERT INTO tv_season_covers(show_id, season_number, cover_url)
            VALUES (?, ?, ?)
            ON CONFLICT(show_id, season_number) DO UPDATE
            SET cover_url = EXCLUDED.cover_url, updated_at = CURRENT_TIMESTAMP
            """.trimIndent(),
            showId,
            seasonNumber,
            url,
        )
    }

    fun remove(
        showId: Long,
        seasonNumber: Int,
    ) {
        jdbc.update("DELETE FROM tv_season_covers WHERE show_id = ? AND season_number = ?", showId, seasonNumber)
    }
}
