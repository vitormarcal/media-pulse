package dev.marcal.mediapulse.server.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class MoviePersonalMarksRepository(
    private val jdbc: JdbcTemplate,
) {
    fun setFavorite(
        id: Long,
        enabled: Boolean,
    ): Boolean = jdbc.update("UPDATE movies SET favorite = ? WHERE id = ?", enabled, id) > 0

    fun setAbandoned(
        id: Long,
        enabled: Boolean,
    ): Boolean = jdbc.update("UPDATE movies SET abandoned = ? WHERE id = ?", enabled, id) > 0
}
