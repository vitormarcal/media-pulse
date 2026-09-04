package dev.marcal.mediapulse.server.repository.crud

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class ShowListItemCrudRepository(
    private val entityManager: EntityManager,
) {
    data class Position(
        val showId: Long,
        val position: Int,
    )

    fun positions(listId: Long): List<Position> =
        entityManager
            .createNativeQuery(
                "SELECT show_id, position FROM show_list_items WHERE list_id = :listId ORDER BY position, id",
            ).setParameter("listId", listId)
            .resultList
            .map {
                val row = it as Array<*>
                Position((row[0] as Number).toLong(), (row[1] as Number).toInt())
            }

    fun upsert(
        listId: Long,
        showId: Long,
    ): Int =
        entityManager
            .createNativeQuery(
                """
                INSERT INTO show_list_items(list_id, show_id, position, updated_at)
                VALUES (:listId, :showId, (SELECT COALESCE(MAX(position), 0) + 1 FROM show_list_items WHERE list_id = :listId), NOW())
                ON CONFLICT (list_id, show_id) DO UPDATE SET updated_at = NOW()
                """.trimIndent(),
            ).setParameter("listId", listId)
            .setParameter("showId", showId)
            .executeUpdate()

    fun remove(
        listId: Long,
        showId: Long,
    ): Int =
        entityManager
            .createNativeQuery("DELETE FROM show_list_items WHERE list_id = :listId AND show_id = :showId")
            .setParameter("listId", listId)
            .setParameter("showId", showId)
            .executeUpdate()

    fun updatePosition(
        listId: Long,
        showId: Long,
        position: Int,
    ): Int =
        entityManager
            .createNativeQuery(
                "UPDATE show_list_items SET position = :position, updated_at = NOW() WHERE list_id = :listId AND show_id = :showId",
            ).setParameter("position", position)
            .setParameter("listId", listId)
            .setParameter("showId", showId)
            .executeUpdate()
}
