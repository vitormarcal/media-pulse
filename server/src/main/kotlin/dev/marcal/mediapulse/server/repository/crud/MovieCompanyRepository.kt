package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.movie.MovieCompany
import org.springframework.data.repository.CrudRepository

interface MovieCompanyRepository : CrudRepository<MovieCompany, Long> {
    fun findByTmdbId(tmdbId: String): MovieCompany?
}
