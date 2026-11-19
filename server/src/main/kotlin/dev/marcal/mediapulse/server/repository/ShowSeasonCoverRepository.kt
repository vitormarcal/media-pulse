package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.shows.ShowImageDto
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Repository
class ShowSeasonCoverRepository(
    private val jdbc: JdbcTemplate,
) {
    @Transactional
    fun save(
        showId: Long,
        seasonNumber: Int,
        url: String,
    ) {
        lock(showId)
        insertImage(showId, seasonNumber, url)
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

    @Transactional
    fun append(
        showId: Long,
        seasonNumber: Int,
        url: String,
    ) {
        lock(showId)
        insertImage(showId, seasonNumber, url)
        jdbc.update(
            "INSERT INTO tv_season_covers(show_id, season_number, cover_url) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
            showId,
            seasonNumber,
            url,
        )
    }

    fun images(
        showId: Long,
        seasonNumber: Int,
    ): List<ShowImageDto> =
        jdbc.query(
            """
            SELECT i.id, i.url, COALESCE(c.cover_url = i.url, false) AS is_primary
            FROM tv_season_images i
            LEFT JOIN tv_season_covers c ON c.show_id = i.show_id AND c.season_number = i.season_number
            WHERE i.show_id = ? AND i.season_number = ? ORDER BY is_primary DESC, i.id DESC
            """.trimIndent(),
            { rs, _ -> ShowImageDto(rs.getLong("id"), rs.getString("url"), rs.getBoolean("is_primary")) },
            showId,
            seasonNumber,
        )

    @Transactional
    fun manage(
        showId: Long,
        seasonNumber: Int,
        url: String,
        remove: Boolean,
    ): String? {
        lock(showId)
        val gallery = images(showId, seasonNumber)
        if (gallery.none { it.url == url }) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem não encontrada nesta temporada.")
        val current = gallery.firstOrNull { it.isPrimary }?.url
        if (!remove) {
            save(showId, seasonNumber, url)
            return url
        }
        jdbc.update("DELETE FROM tv_season_images WHERE show_id = ? AND season_number = ? AND url = ?", showId, seasonNumber, url)
        val cover = if (current == url) gallery.firstOrNull { it.url != url }?.url else current
        if (cover == null) {
            jdbc.update("DELETE FROM tv_season_covers WHERE show_id = ? AND season_number = ?", showId, seasonNumber)
        } else {
            jdbc.update(
                "UPDATE tv_season_covers SET cover_url = ?, updated_at = CURRENT_TIMESTAMP WHERE show_id = ? AND season_number = ?",
                cover,
                showId,
                seasonNumber,
            )
        }
        return cover
    }

    private fun lock(showId: Long) {
        jdbc.queryForObject("SELECT id FROM tv_shows WHERE id = ? FOR UPDATE", Long::class.java, showId)
    }

    private fun insertImage(
        showId: Long,
        seasonNumber: Int,
        url: String,
    ) {
        jdbc.update(
            "INSERT INTO tv_season_images(show_id, season_number, url) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
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
