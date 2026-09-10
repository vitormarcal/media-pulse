package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.movies.PersonFavoriteDto
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.web.server.ResponseStatusException

@Repository
class PersonFavoritesRepository(
    private val jdbc: JdbcTemplate,
) {
    fun findAll(): List<PersonFavoriteDto> =
        jdbc.query(
            """
            SELECT p.id, p.name, p.slug, p.profile_url, p.favorited_at,
              (SELECT COUNT(DISTINCT mc.movie_id) FROM movie_credits mc JOIN movie_watches mw ON mw.movie_id = mc.movie_id WHERE mc.person_id = p.id),
              (SELECT COUNT(DISTINCT sc.show_id) FROM show_credits sc JOIN tv_episodes e ON e.show_id = sc.show_id JOIN tv_episode_watches ew ON ew.episode_id = e.id WHERE sc.person_id = p.id),
              ARRAY_REMOVE(ARRAY[
                CASE WHEN EXISTS (SELECT 1 FROM movie_credits mc WHERE mc.person_id=p.id AND mc.credit_type='CAST') OR EXISTS (SELECT 1 FROM show_credits sc WHERE sc.person_id=p.id AND sc.credit_type='CAST') THEN 'Elenco' END,
                CASE WHEN EXISTS (SELECT 1 FROM movie_credits mc WHERE mc.person_id=p.id AND mc.job='Director') OR EXISTS (SELECT 1 FROM show_credits sc WHERE sc.person_id=p.id AND sc.job='Director') THEN 'Direção' END,
                CASE WHEN EXISTS (SELECT 1 FROM movie_credits mc WHERE mc.person_id=p.id AND mc.job IN ('Writer','Screenplay','Story')) OR EXISTS (SELECT 1 FROM show_credits sc WHERE sc.person_id=p.id AND sc.job IN ('Writer','Screenplay','Story Editor')) THEN 'Roteiro' END
              ], NULL)
            FROM people p WHERE p.favorited_at IS NOT NULL
            ORDER BY p.favorited_at DESC, p.id DESC
            """.trimIndent(),
        ) { rs, _ ->
            PersonFavoriteDto(
                rs.getLong(1),
                rs.getString(2),
                rs.getString(3),
                rs.getString(4),
                ((rs.getArray(8)?.array as? Array<*>) ?: emptyArray<Any>()).mapNotNull { it as? String },
                rs.getLong(6),
                rs.getLong(7),
                rs.getTimestamp(5).toInstant(),
            )
        }

    fun setFavorite(
        personId: Long,
        favorite: Boolean,
    ) {
        val changed =
            if (favorite) {
                jdbc.update("UPDATE people SET favorited_at = COALESCE(favorited_at, NOW()) WHERE id = ?", personId)
            } else {
                jdbc.update("UPDATE people SET favorited_at = NULL WHERE id = ?", personId)
            }
        if (changed == 0) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found")
    }
}
