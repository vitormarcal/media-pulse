package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.movie.MovieCreditType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
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
    )

    fun replaceForMovie(
        movieId: Long,
        credits: List<UpsertMovieCreditRequest>,
    ) {
        jdbc.update(
            """
            DELETE FROM movie_credits
            WHERE movie_id = ?
            """.trimIndent(),
            movieId,
        )

        credits.forEach { credit ->
            insert(credit)
        }
    }

    fun upsert(credit: UpsertMovieCreditRequest) {
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
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
            ON CONFLICT (movie_id, person_id, credit_type, job, character_name)
            DO UPDATE SET
              department = EXCLUDED.department,
              billing_order = EXCLUDED.billing_order,
              updated_at = NOW()
            """.trimIndent(),
            credit.movieId,
            credit.personId,
            credit.creditType.name,
            credit.department,
            credit.job,
            credit.characterName,
            credit.billingOrder,
        )
    }

    private fun insert(credit: UpsertMovieCreditRequest) {
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
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
            """.trimIndent(),
            credit.movieId,
            credit.personId,
            credit.creditType.name,
            credit.department,
            credit.job,
            credit.characterName,
            credit.billingOrder,
        )
    }
}
