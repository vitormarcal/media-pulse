package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.movie.Movie
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface MovieRepository : CrudRepository<Movie, Long> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = "UPDATE movies SET cover_url = :url, updated_at = NOW() WHERE id = :id")
    fun updateCover(
        id: Long,
        url: String?,
    ): Int

    @Query(nativeQuery = true, value = "SELECT cover_url FROM movies WHERE id = :id")
    fun storedCover(id: Long): String?

    fun findByFingerprint(fingerprint: String): Movie?

    fun findByTmdbId(tmdbId: String): Movie?

    fun findByImdbId(imdbId: String): Movie?

    @Query(
        nativeQuery = true,
        value = """
            SELECT m.*
            FROM movies m
            JOIN movie_titles mt ON mt.movie_id = m.id
            WHERE LOWER(mt.title) = LOWER(:title)
              AND m.year IS NOT DISTINCT FROM :year
            ORDER BY mt.is_primary ASC, mt.id ASC
            LIMIT 1
        """,
    )
    fun findByMovieTitleAndYear(
        title: String,
        year: Int?,
    ): Movie?
}
