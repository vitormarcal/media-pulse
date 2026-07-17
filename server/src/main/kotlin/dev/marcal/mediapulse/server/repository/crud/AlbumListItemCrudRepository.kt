package dev.marcal.mediapulse.server.repository.crud

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class AlbumListItemCrudRepository(
    private val entityManager: EntityManager,
) {
    fun add(
        listId: Long,
        albumId: Long,
    ) = entityManager
        .createNativeQuery(
            """
            INSERT INTO album_list_items (list_id, album_id, position)
            SELECT :listId, :albumId, COALESCE(MAX(position), 0) + 1
            FROM album_list_items WHERE list_id = :listId
            ON CONFLICT (list_id, album_id) DO NOTHING
            """.trimIndent(),
        ).setParameter("listId", listId)
        .setParameter("albumId", albumId)
        .executeUpdate()

    fun remove(
        listId: Long,
        albumId: Long,
    ) = entityManager
        .createNativeQuery(
            "DELETE FROM album_list_items WHERE list_id = :listId AND album_id = :albumId",
        ).setParameter("listId", listId)
        .setParameter("albumId", albumId)
        .executeUpdate()

    fun setPosition(
        listId: Long,
        albumId: Long,
        position: Int,
    ) = entityManager
        .createNativeQuery(
            "UPDATE album_list_items SET position = :position, updated_at = NOW() WHERE list_id = :listId AND album_id = :albumId",
        ).setParameter("position", position)
        .setParameter("listId", listId)
        .setParameter("albumId", albumId)
        .executeUpdate()

    fun setListenedAt(
        listId: Long,
        albumId: Long,
        listenedAt: Instant?,
    ) = entityManager
        .createNativeQuery(
            "UPDATE album_list_items SET listened_at = :listenedAt, updated_at = NOW() WHERE list_id = :listId AND album_id = :albumId",
        ).setParameter("listenedAt", listenedAt)
        .setParameter("listId", listId)
        .setParameter("albumId", albumId)
        .executeUpdate()
}
