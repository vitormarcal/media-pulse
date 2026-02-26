package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.movie.MovieTitle
import dev.marcal.mediapulse.server.model.movie.MovieTitleSource
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional

interface MovieTitleCrudRepository : CrudRepository<MovieTitle, Long> {
    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO movie_titles(movie_id, title, locale, source, is_primary)
            VALUES (:movieId, :title, :locale, :source, :isPrimary)
            ON CONFLICT DO NOTHING
        """,
    )
    fun insertIgnore(
        movieId: Long,
        title: String,
        locale: String?,
        source: String,
        isPrimary: Boolean,
    )

    fun findByMovieIdAndTitleAndSource(
        movieId: Long,
        title: String,
        source: MovieTitleSource,
    ): MovieTitle?
}
