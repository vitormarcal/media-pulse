package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.movie.MovieCompanyType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class MovieCompanyAssignmentRepository(
    private val jdbc: JdbcTemplate,
) {
    data class UpsertMovieCompanyRequest(
        val movieId: Long,
        val companyId: Long,
        val companyType: MovieCompanyType,
    )

    fun replaceForMovie(
        movieId: Long,
        companies: List<UpsertMovieCompanyRequest>,
    ) {
        jdbc.update(
            """
            DELETE FROM movie_company_assignments
            WHERE movie_id = ?
            """.trimIndent(),
            movieId,
        )

        companies.forEach(::insert)
    }

    fun upsert(company: UpsertMovieCompanyRequest) {
        jdbc.update(
            """
            INSERT INTO movie_company_assignments (
                movie_id,
                company_id,
                company_type,
                updated_at
            )
            VALUES (?, ?, ?, NOW())
            ON CONFLICT (movie_id, company_id, company_type)
            DO UPDATE SET updated_at = NOW()
            """.trimIndent(),
            company.movieId,
            company.companyId,
            company.companyType.name,
        )
    }

    private fun insert(company: UpsertMovieCompanyRequest) {
        jdbc.update(
            """
            INSERT INTO movie_company_assignments (
                movie_id,
                company_id,
                company_type,
                updated_at
            )
            VALUES (?, ?, ?, NOW())
            """.trimIndent(),
            company.movieId,
            company.companyId,
            company.companyType.name,
        )
    }
}
