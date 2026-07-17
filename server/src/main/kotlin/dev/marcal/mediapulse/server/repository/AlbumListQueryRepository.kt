package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.music.AlbumListDetailsResponse
import dev.marcal.mediapulse.server.api.music.AlbumListItemDto
import dev.marcal.mediapulse.server.api.music.AlbumListSummaryDto
import jakarta.persistence.EntityManager
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Repository
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Repository
class AlbumListQueryRepository(
    private val entityManager: EntityManager,
) {
    fun listAll(): List<AlbumListSummaryDto> {
        val rows =
            entityManager
                .createNativeQuery(
                    """
                    SELECT al.id, al.name, al.slug, al.description,
                           COUNT(ali.id), COUNT(ali.listened_at), al.updated_at
                    FROM album_lists al
                    LEFT JOIN album_list_items ali ON ali.list_id = al.id
                    GROUP BY al.id
                    ORDER BY al.updated_at DESC, al.id DESC
                    """.trimIndent(),
                ).resultList
        return rows.map { row ->
            val fields = row as Array<*>
            val listId = (fields[0] as Number).toLong()
            AlbumListSummaryDto(
                listId = listId,
                name = fields[1] as String,
                slug = fields[2] as String,
                description = fields[3] as String?,
                itemCount = (fields[4] as Number).toInt(),
                listenedCount = (fields[5] as Number).toInt(),
                coverUrls = coverUrls(listId),
                updatedAt = fields[6] as Instant,
            )
        }
    }

    fun details(slug: String): AlbumListDetailsResponse {
        val base =
            entityManager
                .createNativeQuery(
                    """
                    SELECT al.id, al.name, al.slug, al.description
                    FROM album_lists al
                    WHERE al.slug = :slug
                    """.trimIndent(),
                ).setParameter("slug", slug)
                .resultList
                .firstOrNull() as Array<*>?
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Album list not found")
        val listId = (base[0] as Number).toLong()
        val items = items(listId)
        return AlbumListDetailsResponse(
            listId = listId,
            name = base[1] as String,
            slug = base[2] as String,
            description = base[3] as String?,
            itemCount = items.size,
            listenedCount = items.count { it.listenedAt != null },
            items = items,
        )
    }

    fun itemIds(listId: Long): List<Long> =
        entityManager
            .createNativeQuery(
                "SELECT album_id FROM album_list_items WHERE list_id = :listId ORDER BY position, id",
            ).setParameter("listId", listId)
            .resultList
            .map { (it as Number).toLong() }

    private fun items(listId: Long): List<AlbumListItemDto> =
        entityManager
            .createNativeQuery(
                """
                SELECT a.id, a.title, ar.id, ar.name, a.year, a.cover_url,
                       ali.position, ali.listened_at, mr.rating
                FROM album_list_items ali
                JOIN albums a ON a.id = ali.album_id
                JOIN artists ar ON ar.id = a.artist_id
                LEFT JOIN media_ratings mr ON mr.entity_type = 'ALBUM' AND mr.entity_id = a.id
                WHERE ali.list_id = :listId
                ORDER BY ali.position ASC, ali.id ASC
                """.trimIndent(),
            ).setParameter("listId", listId)
            .resultList
            .map { row ->
                val fields = row as Array<*>
                AlbumListItemDto(
                    albumId = (fields[0] as Number).toLong(),
                    albumTitle = fields[1] as String,
                    artistId = (fields[2] as Number).toLong(),
                    artistName = fields[3] as String,
                    year = (fields[4] as Number?)?.toInt(),
                    coverUrl = fields[5] as String?,
                    position = (fields[6] as Number).toInt(),
                    listenedAt = fields[7] as Instant?,
                    rating = (fields[8] as Number?)?.toInt(),
                )
            }

    private fun coverUrls(listId: Long): List<String> =
        entityManager
            .createNativeQuery(
                """
                SELECT a.cover_url
                FROM album_list_items ali
                JOIN albums a ON a.id = ali.album_id
                WHERE ali.list_id = :listId AND a.cover_url IS NOT NULL
                ORDER BY ali.position, ali.id
                LIMIT 3
                """.trimIndent(),
            ).setParameter("listId", listId)
            .resultList
            .map { it as String }
}
