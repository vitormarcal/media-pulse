package dev.marcal.mediapulse.server.repository.crud

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class MovieCollectionCrudRepository(
    private val entityManager: EntityManager,
) {
    data class MovieCollectionRecord(
        val id: Long,
        val tmdbId: String,
        val name: String,
        val posterUrl: String?,
        val backdropUrl: String?,
        val overview: String?,
    )

    data class MovieCollectionBackfillCandidate(
        val movieId: Long,
        val tmdbId: String,
    )

    data class MovieCollectionMemberRecord(
        val tmdbId: String,
        val title: String,
        val originalTitle: String?,
        val year: Int?,
        val overview: String?,
        val posterUrl: String?,
        val backdropUrl: String?,
        val localMovieId: Long?,
        val localSlug: String?,
    )

    data class MovieCollectionMemberSnapshot(
        val tmdbId: String,
        val title: String,
        val originalTitle: String?,
        val year: Int?,
        val overview: String?,
        val posterUrl: String?,
        val backdropUrl: String?,
    )

    fun findCollection(collectionId: Long): MovieCollectionRecord? =
        (
            entityManager
                .createNativeQuery(
                    """
                    SELECT id, tmdb_id, name, poster_url, backdrop_url, overview
                    FROM movie_collections
                    WHERE id = :collectionId
                    LIMIT 1
                    """.trimIndent(),
                ).setParameter("collectionId", collectionId)
                .resultList
                .firstOrNull() as Array<*>?
        )?.let { fields ->
            MovieCollectionRecord(
                id = (fields[0] as Number).toLong(),
                tmdbId = fields[1] as String,
                name = fields[2] as String,
                posterUrl = fields[3] as String?,
                backdropUrl = fields[4] as String?,
                overview = fields[5] as String?,
            )
        }

    fun findPendingCollectionIds(limit: Int): List<Long> =
        entityManager
            .createNativeQuery(
                """
                SELECT id
                FROM movie_collections
                WHERE members_synced_at IS NULL
                  AND (
                    members_sync_attempted_at IS NULL
                    OR members_sync_attempted_at <= NOW() - INTERVAL '1 day'
                  )
                ORDER BY members_sync_attempted_at NULLS FIRST, id
                LIMIT :limit
                """.trimIndent(),
            ).setParameter("limit", limit)
            .resultList
            .map { (it as Number).toLong() }

    fun findMembers(collectionId: Long): List<MovieCollectionMemberRecord> =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  member.tmdb_id,
                  member.title,
                  member.original_title,
                  member.release_year,
                  member.overview,
                  member.poster_url,
                  member.backdrop_url,
                  movie.id,
                  movie.slug
                FROM movie_collection_members member
                LEFT JOIN movies movie ON movie.tmdb_id = member.tmdb_id
                WHERE member.collection_id = :collectionId
                ORDER BY member.position, member.id
                """.trimIndent(),
            ).setParameter("collectionId", collectionId)
            .resultList
            .map { row ->
                val fields = row as Array<*>
                MovieCollectionMemberRecord(
                    tmdbId = fields[0] as String,
                    title = fields[1] as String,
                    originalTitle = fields[2] as String?,
                    year = (fields[3] as Number?)?.toInt(),
                    overview = fields[4] as String?,
                    posterUrl = fields[5] as String?,
                    backdropUrl = fields[6] as String?,
                    localMovieId = (fields[7] as Number?)?.toLong(),
                    localSlug = fields[8] as String?,
                )
            }

    fun replaceMemberSnapshot(
        collectionId: Long,
        name: String,
        overview: String?,
        posterUrl: String?,
        backdropUrl: String?,
        members: List<MovieCollectionMemberSnapshot>,
    ) {
        entityManager
            .createNativeQuery("DELETE FROM movie_collection_members WHERE collection_id = :collectionId")
            .setParameter("collectionId", collectionId)
            .executeUpdate()

        members.forEachIndexed { position, member ->
            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO movie_collection_members(
                      collection_id, tmdb_id, title, original_title, release_year,
                      overview, poster_url, backdrop_url, position, updated_at
                    ) VALUES (
                      :collectionId, :tmdbId, :title, :originalTitle, :releaseYear,
                      :overview, :posterUrl, :backdropUrl, :position, NOW()
                    )
                    """.trimIndent(),
                ).setParameter("collectionId", collectionId)
                .setParameter("tmdbId", member.tmdbId)
                .setParameter("title", member.title)
                .setParameter("originalTitle", member.originalTitle)
                .setParameter("releaseYear", member.year)
                .setParameter("overview", member.overview)
                .setParameter("posterUrl", member.posterUrl)
                .setParameter("backdropUrl", member.backdropUrl)
                .setParameter("position", position)
                .executeUpdate()
        }

        entityManager
            .createNativeQuery(
                """
                UPDATE movie_collections
                SET name = :name,
                    overview = :overview,
                    poster_url = COALESCE(:posterUrl, poster_url),
                    backdrop_url = COALESCE(:backdropUrl, backdrop_url),
                    members_synced_at = NOW(),
                    members_sync_attempted_at = NOW(),
                    members_sync_error = NULL,
                    updated_at = NOW()
                WHERE id = :collectionId
                """.trimIndent(),
            ).setParameter("collectionId", collectionId)
            .setParameter("name", name)
            .setParameter("overview", overview)
            .setParameter("posterUrl", posterUrl)
            .setParameter("backdropUrl", backdropUrl)
            .executeUpdate()
    }

    fun markMemberSyncFailure(
        collectionId: Long,
        error: String,
    ) {
        entityManager
            .createNativeQuery(
                """
                UPDATE movie_collections
                SET members_sync_attempted_at = NOW(),
                    members_sync_error = :error,
                    updated_at = NOW()
                WHERE id = :collectionId
                """.trimIndent(),
            ).setParameter("collectionId", collectionId)
            .setParameter("error", error.take(500))
            .executeUpdate()
    }

    fun findBackfillCandidates(limit: Int): List<MovieCollectionBackfillCandidate> =
        entityManager
            .createNativeQuery(
                """
                SELECT m.id, m.tmdb_id
                FROM movies m
                WHERE m.collection_id IS NULL
                  AND m.collection_checked_at IS NULL
                  AND m.tmdb_id IS NOT NULL
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
