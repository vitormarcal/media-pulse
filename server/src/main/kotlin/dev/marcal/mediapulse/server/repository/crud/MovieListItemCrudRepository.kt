package dev.marcal.mediapulse.server.repository.crud

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class MovieListItemCrudRepository(
    private val entityManager: EntityManager,
) {
    data class MovieListPositionRecord(
        val movieId: Long,
        val position: Int,
    )

    fun nextPosition(listId: Long): Int =
        (
            entityManager
                .createNativeQuery(
                    """
                    SELECT COALESCE(MAX(mli.position), 0) + 1
                    FROM movie_list_items mli
                    WHERE mli.list_id = :listId
                    """.trimIndent(),
                ).setParameter("listId", listId)
                .singleResult as Number
        ).toInt()

    fun upsertItem(
        listId: Long,
        movieId: Long,
    ): Int =
        entityManager
            .createNativeQuery(
                """
                INSERT INTO movie_list_items (
                    list_id,
                    movie_id,
                    position,
                    updated_at
                )
                VALUES (
                    :listId,
                    :movieId,
                    :position,
                    NOW()
                )
                ON CONFLICT (list_id, movie_id)
                DO UPDATE SET updated_at = NOW()
                """.trimIndent(),
            ).setParameter("listId", listId)
            .setParameter("movieId", movieId)
            .setParameter("position", nextPosition(listId))
            .executeUpdate()

    fun removeItem(
        listId: Long,
        movieId: Long,
    ): Int =
        entityManager
            .createNativeQuery(
                """
                DELETE FROM movie_list_items
                WHERE list_id = :listId
                  AND movie_id = :movieId
                """.trimIndent(),
            ).setParameter("listId", listId)
            .setParameter("movieId", movieId)
            .executeUpdate()

    fun listPositions(listId: Long): List<MovieListPositionRecord> =
        entityManager
            .createNativeQuery(
                """
                SELECT movie_id, position
                FROM movie_list_items
                WHERE list_id = :listId
                ORDER BY position ASC, id ASC
                """.trimIndent(),
            ).setParameter("listId", listId)
            .resultList
            .map { row ->
                val fields = row as Array<*>
                MovieListPositionRecord(
                    movieId = (fields[0] as Number).toLong(),
                    position = (fields[1] as Number).toInt(),
                )
            }

    fun updatePosition(
        listId: Long,
        movieId: Long,
        position: Int,
    ): Int =
        entityManager
            .createNativeQuery(
                """
                UPDATE movie_list_items
                SET position = :position,
                    updated_at = NOW()
                WHERE list_id = :listId
                  AND movie_id = :movieId
                """.trimIndent(),
            ).setParameter("position", position)
            .setParameter("listId", listId)
            .setParameter("movieId", movieId)
            .executeUpdate()
}
