package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.movie.MovieWatch
import dev.marcal.mediapulse.server.model.movie.MovieWatchSource
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface MovieWatchCrudRepository : CrudRepository<MovieWatch, Long> {
    fun existsByMovieIdAndSourceAndWatchedAt(
        movieId: Long,
        source: MovieWatchSource,
        watchedAt: Instant,
    ): Boolean

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO movie_watches(movie_id, source, watched_at)
            VALUES (:movieId, :source, :watchedAt)
            ON CONFLICT DO NOTHING
        """,
    )
    fun insertIgnore(
        movieId: Long,
        source: String,
        watchedAt: Instant,
    )
}
