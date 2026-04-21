package dev.marcal.mediapulse.server.repository.crud

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class MovieCollectionCrudRepository(
    private val entityManager: EntityManager,
) {
    data class MovieCollectionBackfillCandidate(
        val movieId: Long,
        val tmdbId: String,
    )

    fun findBackfillCandidates(limit: Int): List<MovieCollectionBackfillCandidate> =
        entityManager
            .createNativeQuery(
                """
                SELECT m.id, ei.external_id
                FROM movies m
                JOIN external_identifiers ei
                  ON ei.entity_type = 'MOVIE'
                 AND ei.entity_id = m.id
                 AND ei.provider = 'TMDB'
                WHERE m.collection_id IS NULL
                  AND m.collection_checked_at IS NULL
                ORDER BY m.id ASC
                LIMIT :limit
                """.trimIndent(),
            ).setParameter("limit", limit.coerceAtLeast(1))
            .resultList
            .map { row ->
                val fields = row as Array<*>
                MovieCollectionBackfillCandidate(
                    movieId = (fields[0] as Number).toLong(),
                    tmdbId = fields[1] as String,
                )
            }

    fun markCollectionChecked(movieId: Long): Int =
        entityManager
            .createNativeQuery(
                """
                UPDATE movies
                SET collection_checked_at = NOW(),
                    updated_at = NOW()
                WHERE id = :movieId
                """.trimIndent(),
            ).setParameter("movieId", movieId)
            .executeUpdate()

    fun upsertFromTmdb(
        tmdbId: String,
        name: String,
        posterUrl: String?,
        backdropUrl: String?,
    ): Long =
        (
            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO movie_collections(tmdb_id, name, poster_url, backdrop_url, updated_at)
                    VALUES (:tmdbId, :name, :posterUrl, :backdropUrl, NOW())
                    ON CONFLICT (tmdb_id) DO UPDATE
                    SET name = EXCLUDED.name,
                        poster_url = COALESCE(EXCLUDED.poster_url, movie_collections.poster_url),
                        backdrop_url = COALESCE(EXCLUDED.backdrop_url, movie_collections.backdrop_url),
                        updated_at = NOW()
                    RETURNING id
                    """.trimIndent(),
                ).setParameter("tmdbId", tmdbId)
                .setParameter("name", name)
                .setParameter("posterUrl", posterUrl)
                .setParameter("backdropUrl", backdropUrl)
                .singleResult as Number
        ).toLong()
}
