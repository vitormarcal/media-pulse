package dev.marcal.mediapulse.server.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ShowPersonalMarksRepository(
    private val jdbc: JdbcTemplate,
) {
    fun setFavorite(
        id: Long,
        enabled: Boolean,
    ): Boolean = jdbc.update("UPDATE tv_shows SET favorite = ? WHERE id = ?", enabled, id) > 0

    fun setAbandoned(
        id: Long,
        enabled: Boolean,
    ): Boolean = jdbc.update("UPDATE tv_shows SET abandoned = ? WHERE id = ?", enabled, id) > 0
}
