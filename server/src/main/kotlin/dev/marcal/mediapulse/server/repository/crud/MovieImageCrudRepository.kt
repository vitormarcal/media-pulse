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
            UPDATE movie_images
            SET is_primary = (url = :url)
            WHERE movie_id = :movieId
        """,
    )
    fun setPrimaryForMovie(
        movieId: Long,
        url: String,
    )
}
