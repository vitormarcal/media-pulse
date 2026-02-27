package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.movie.Movie
import org.springframework.data.repository.CrudRepository

interface MovieRepository : CrudRepository<Movie, Long> {
    fun findByFingerprint(fingerprint: String): Movie?
}
