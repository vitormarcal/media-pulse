package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.movie.MoviePerson
import org.springframework.data.repository.CrudRepository

interface MoviePersonRepository : CrudRepository<MoviePerson, Long> {
    fun findByTmdbId(tmdbId: String): MoviePerson?

    fun findBySlug(slug: String): MoviePerson?
}
