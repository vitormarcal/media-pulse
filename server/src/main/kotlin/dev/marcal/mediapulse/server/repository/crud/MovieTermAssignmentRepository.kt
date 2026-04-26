package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.movie.MovieTermSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class MovieTermAssignmentRepository(
    private val jdbc: JdbcTemplate,
) {
    fun upsert(
        movieId: Long,
        termId: Long,
        source: MovieTermSource,
    ) {
        jdbc.update(
            """
            INSERT INTO movie_term_assignments (movie_id, term_id, source, hidden, updated_at)
            VALUES (?, ?, ?, FALSE, NOW())
            ON CONFLICT (movie_id, term_id)
            DO UPDATE SET
              source = EXCLUDED.source,
              hidden = FALSE,
              updated_at = NOW()
            """.trimIndent(),
            movieId,
            termId,
            source.name,
        )
    }

    fun updateVisibility(
        movieId: Long,
        termId: Long,
        hidden: Boolean,
    ): Int =
        jdbc.update(
            """
            UPDATE movie_term_assignments
            SET hidden = ?, updated_at = NOW()
            WHERE movie_id = ? AND term_id = ?
            """.trimIndent(),
            hidden,
            movieId,
            termId,
        )
}
