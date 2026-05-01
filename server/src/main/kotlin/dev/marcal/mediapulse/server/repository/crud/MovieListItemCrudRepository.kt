package dev.marcal.mediapulse.server.repository.crud

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class MovieListItemCrudRepository(
    private val entityManager: EntityManager,
) {
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
}
