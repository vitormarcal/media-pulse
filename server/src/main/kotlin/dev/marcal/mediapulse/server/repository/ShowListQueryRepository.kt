package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.shows.ShowListDetailsResponse
import dev.marcal.mediapulse.server.api.shows.ShowListItemDto
import dev.marcal.mediapulse.server.api.shows.ShowListPreviewShowDto
import dev.marcal.mediapulse.server.api.shows.ShowListSummaryDto
import jakarta.persistence.EntityManager
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Repository
import org.springframework.web.server.ResponseStatusException

@Repository
class ShowListQueryRepository(
    private val entityManager: EntityManager,
) {
    fun forShow(showId: Long): List<ShowListSummaryDto> =
        summaries("WHERE EXISTS (SELECT 1 FROM show_list_members x WHERE x.list_id = sl.id AND x.show_id = :showId)", showId)

    fun listAll(): List<ShowListSummaryDto> = summaries()

    fun summary(listId: Long): ShowListSummaryDto? = summaries("WHERE sl.id = :listId", listId = listId).firstOrNull()

    private fun summaries(
        filter: String = "",
        showId: Long? = null,
        listId: Long? = null,
    ): List<ShowListSummaryDto> {
        val query =
            entityManager.createNativeQuery(
                """
                SELECT sl.id, sl.name, sl.slug, sl.description, sl.cover_show_id, cs.cover_url, COUNT(sli.id), sl.include_favorites, sl.include_abandoned
                FROM show_lists sl
                LEFT JOIN tv_shows cs ON cs.id = sl.cover_show_id
                LEFT JOIN show_list_members sli ON sli.list_id = sl.id
                $filter
                GROUP BY sl.id, sl.name, sl.slug, sl.description, sl.cover_show_id, cs.cover_url
                ORDER BY COALESCE(sl.updated_at, sl.created_at) DESC, sl.name
                """.trimIndent(),
            )
        showId?.let { query.setParameter("showId", it) }
        listId?.let { query.setParameter("listId", it) }
        val rows = query.resultList
        val ids = rows.map { ((it as Array<*>)[0] as Number).toLong() }
        val previews = previews(ids)
        return rows.map {
            val row = it as Array<*>
            val id = (row[0] as Number).toLong()
            ShowListSummaryDto(
                id,
                row[1] as String,
                row[2] as String,
                row[3] as String?,
                (row[6] as Number).toLong(),
                (row[4] as Number?)?.toLong(),
                row[5] as String?,
                previews[id].orEmpty(),
                row[7] as Boolean,
                row[8] as Boolean,
                showId != null &&
                    !(
                        entityManager
                            .createNativeQuery(
                                "SELECT EXISTS (SELECT 1 FROM show_list_items WHERE list_id = :listId AND show_id = :showId)",
                            ).setParameter("listId", id)
                            .setParameter("showId", showId)
                            .singleResult as Boolean
                    ),
            )
        }
    }

    fun details(slug: String): ShowListDetailsResponse {
        val list =
            entityManager
                .createNativeQuery(
                    """
                    SELECT sl.id, sl.name, sl.slug, sl.description, sl.cover_show_id, cs.cover_url, sl.include_favorites, sl.include_abandoned
                    FROM show_lists sl LEFT JOIN tv_shows cs ON cs.id = sl.cover_show_id WHERE sl.slug = :slug
                    """.trimIndent(),
                ).setParameter("slug", slug.trim())
                .resultList
                .firstOrNull() as Array<*>?
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Show list not found")
        val id = (list[0] as Number).toLong()
        val shows =
            entityManager
                .createNativeQuery(
                    """
                    SELECT s.id,
                      COALESCE((SELECT st.title FROM tv_show_titles st WHERE st.show_id=s.id ORDER BY st.is_primary ASC, st.id LIMIT 1), s.original_title),
                      s.original_title, s.slug, s.year, s.cover_url,
                      COUNT(DISTINCT e.id), COUNT(DISTINCT CASE WHEN w.id IS NOT NULL THEN e.id END)
                    FROM show_list_members sli JOIN tv_shows s ON s.id=sli.show_id
                    LEFT JOIN tv_episodes e ON e.show_id=s.id LEFT JOIN tv_episode_watches w ON w.episode_id=e.id
                    WHERE sli.list_id=:listId
                    GROUP BY s.id, s.original_title, s.slug, s.year, s.cover_url, sli.position, sli.id
                    ORDER BY sli.position NULLS LAST, s.id
                    """.trimIndent(),
                ).setParameter("listId", id)
                .resultList
                .map {
                    val row = it as Array<*>
                    ShowListItemDto(
                        (row[0] as Number).toLong(),
                        row[1] as String,
                        row[2] as String,
                        row[3] as String?,
                        (row[4] as Number?)?.toInt(),
                        row[5] as String?,
                        (row[6] as Number).toLong(),
                        (row[7] as Number).toLong(),
                    )
                }
        return ShowListDetailsResponse(
            id,
            list[1] as String,
            list[2] as String,
            list[3] as String?,
            (list[4] as Number?)?.toLong(),
            list[5] as String?,
            shows.size.toLong(),
            shows.count { it.episodesCount > 0 && it.watchedEpisodesCount == it.episodesCount }.toLong(),
            shows,
            list[6] as Boolean,
            list[7] as Boolean,
            entityManager
                .createNativeQuery(
                    "SELECT show_id FROM show_list_items WHERE list_id = :id ORDER BY position, id",
                ).setParameter("id", id)
                .resultList
                .map {
                    (it as Number).toLong()
                },
        )
    }

    private fun previews(ids: List<Long>): Map<Long, List<ShowListPreviewShowDto>> {
        if (ids.isEmpty()) return emptyMap()
        val rows =
            entityManager
                .createNativeQuery(
                    """
                    SELECT list_id, show_id, title, slug, cover_url FROM (
                      SELECT sli.list_id, s.id show_id,
                        COALESCE((SELECT st.title FROM tv_show_titles st WHERE st.show_id=s.id ORDER BY st.is_primary ASC, st.id LIMIT 1), s.original_title) title,
                        s.slug, s.cover_url, ROW_NUMBER() OVER(PARTITION BY sli.list_id ORDER BY sli.position NULLS LAST, s.id) rank
                      FROM show_list_members sli JOIN tv_shows s ON s.id=sli.show_id WHERE sli.list_id IN (:ids)
                    ) ranked WHERE rank <= 3 ORDER BY list_id, rank
                    """.trimIndent(),
                ).setParameter("ids", ids)
                .resultList
        return rows.groupBy({ ((it as Array<*>)[0] as Number).toLong() }, {
            val row = it as Array<*>
            ShowListPreviewShowDto((row[1] as Number).toLong(), row[2] as String, row[3] as String?, row[4] as String?)
        })
    }
}
