package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.movie.MovieList
import org.springframework.data.repository.CrudRepository

interface MovieListRepository : CrudRepository<MovieList, Long> {
    fun findBySlug(slug: String): MovieList?

    fun findByNormalizedName(normalizedName: String): MovieList?
}
