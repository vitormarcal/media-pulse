package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.movie.MovieCreditType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional
class MovieCreditAssignmentRepository(
    private val jdbc: JdbcTemplate,
) {
    data class UpsertMovieCreditRequest(
        val movieId: Long,
        val personId: Long,
        val creditType: MovieCreditType,
        val department: String = "",
        val job: String = "",
        val characterName: String = "",
        val billingOrder: Int? = null,
        val manuallySelected: Boolean = false,
    )

    fun replaceForMovie(
        movieId: Long,
        credits: List<UpsertMovieCreditRequest>,
    ) {
        lockMovie(movieId)
        jdbc.update(
            """
            DELETE FROM movie_credits
            WHERE movie_id = ? AND NOT manually_selected
            """.trimIndent(),
            movieId,
        )

        credits.forEach { credit ->
            upsert(credit)
        }
    }

    fun upsert(credit: UpsertMovieCreditRequest) {
        lockMovie(credit.movieId)
        jdbc.update(
            """
            INSERT INTO movie_credits (
                movie_id,
                person_id,
                credit_type,
                department,
                job,
                character_name,
                billing_order,
                manually_selected,
                updated_at
            )
            SELECT ?, ?, ?, ?, ?, ?, ?, ?, NOW()
            WHERE ? OR NOT EXISTS (
                SELECT 1 FROM movie_credits protected
                WHERE protected.movie_id = ? AND protected.person_id = ? AND protected.manually_selected
                  AND CASE WHEN protected.credit_type = 'CAST' THEN 'CAST'
                           WHEN protected.job = 'Director' THEN 'DIRECTING' ELSE 'WRITING' END = ?
            )
            ON CONFLICT (movie_id, person_id, credit_type, job, character_name)
            DO UPDATE SET
              department = EXCLUDED.department,
              billing_order = EXCLUDED.billing_order,
              manually_selected = movie_credits.manually_selected OR EXCLUDED.manually_selected,
              updated_at = NOW()
            """.trimIndent(),
            credit.movieId,
            credit.personId,
            credit.creditType.name,
            credit.department,
            credit.job,
            credit.characterName,
            credit.billingOrder,
            credit.manuallySelected,
            credit.manuallySelected,
            credit.movieId,
            credit.personId,
            when {
                credit.creditType == MovieCreditType.CAST -> "CAST"
                credit.job == "Director" -> "DIRECTING"
                else -> "WRITING"
            },
        )
    }

    fun deleteCategory(
        movieId: Long,
        personId: Long,
        category: String,
    ): Int {
        lockMovie(movieId)
        val predicate =
            when (category) {
                "CAST" -> "credit_type = 'CAST'"
                "DIRECTING" -> "credit_type = 'CREW' AND job = 'Director'"
                "WRITING" -> "credit_type = 'CREW' AND job IN ('Writer', 'Screenplay', 'Story')"
                else -> return 0
            }
        return jdbc.update("DELETE FROM movie_credits WHERE movie_id = ? AND person_id = ? AND $predicate", movieId, personId)
    }

    private fun lockMovie(movieId: Long) {
        jdbc.queryForObject("SELECT id FROM movies WHERE id = ? FOR UPDATE", Long::class.java, movieId)
    }
}
