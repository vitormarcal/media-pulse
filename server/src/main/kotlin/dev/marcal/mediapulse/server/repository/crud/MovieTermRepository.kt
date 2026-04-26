package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.movie.MovieTerm
import dev.marcal.mediapulse.server.model.movie.MovieTermKind
import org.springframework.data.repository.CrudRepository

interface MovieTermRepository : CrudRepository<MovieTerm, Long> {
    fun findByKindAndNormalizedName(
        kind: MovieTermKind,
        normalizedName: String,
    ): MovieTerm?
}
