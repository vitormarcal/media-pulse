package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.movie.MovieImage
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional

interface MovieImageCrudRepository : CrudRepository<MovieImage, Long> {
    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO movie_images(movie_id, url, is_primary)
            VALUES (:movieId, :url, :isPrimary)
            ON CONFLICT DO NOTHING
        """,
    )
    fun insertIgnore(
        movieId: Long,
        url: String,
        isPrimary: Boolean,
    )

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            SELECT id
            FROM movies
            WHERE id = :movieId
            FOR UPDATE
        """,
    )
    fun lockMovieRowForPrimaryUpdate(movieId: Long): Long?

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            UPDATE movie_images
            SET is_primary = FALSE
            WHERE movie_id = :movieId
              AND is_primary = TRUE
        """,
    )
    fun clearPrimaryForMovie(movieId: Long): Int

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            UPDATE movie_images
            SET is_primary = TRUE
            WHERE movie_id = :movieId
              AND url = :url
        """,
    )
    fun markPrimaryForMovie(
        movieId: Long,
        url: String,
    ): Int
}
