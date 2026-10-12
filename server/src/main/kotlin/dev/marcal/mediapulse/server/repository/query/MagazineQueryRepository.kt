package dev.marcal.mediapulse.server.repository.query

import dev.marcal.mediapulse.server.model.magazine.MagazineIssueOrder
import dev.marcal.mediapulse.server.model.magazine.MagazineReadStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class MagazineQueryRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun publicationCounts(): Map<Long, Long> =
        jdbc
            .query(
                "SELECT publication_id, count(*) AS total FROM magazine_issues GROUP BY publication_id",
                emptyMap<String, Any>(),
            ) { rs, _ ->
                rs.getLong("publication_id") to rs.getLong("total")
            }.toMap()

    fun publicationLatestIssueIds(): List<Long> =
        jdbc.query(
            """
            SELECT DISTINCT ON (publication_id) id FROM magazine_issues
            ORDER BY publication_id, cover_date DESC NULLS LAST,
              CASE WHEN number ~ '^[0-9]+$' THEN CAST(number AS NUMERIC) END DESC NULLS LAST,
              lower(number) DESC NULLS LAST, id DESC
            """.trimIndent(),
            emptyMap<String, Any>(),
        ) { rs, _ -> rs.getLong("id") }

    fun issueIds(
        q: String?,
        publicationId: Long?,
        status: MagazineReadStatus?,
        page: Int,
        limit: Int,
        unread: Boolean = false,
        order: MagazineIssueOrder = MagazineIssueOrder.PUBLICATION_DATE,
    ): List<Long> =
        jdbc.query(
            """
            SELECT i.id FROM magazine_issues i
            JOIN magazine_publications p ON p.id = i.publication_id
            LEFT JOIN LATERAL (SELECT status FROM magazine_reads WHERE issue_id = i.id ORDER BY (status IN ('WANT_TO_READ', 'CURRENTLY_READING')) DESC, id DESC LIMIT 1) r ON true
            WHERE (CAST(:publicationId AS BIGINT) IS NULL OR p.id = :publicationId)
              AND (CAST(:status AS TEXT) IS NULL OR r.status = :status)
              AND (:unread = false OR NOT EXISTS (SELECT 1 FROM magazine_reads completed WHERE completed.issue_id = i.id AND completed.status = 'READ'))
              AND (CAST(:q AS TEXT) IS NULL OR strpos(lower(concat_ws(' ', p.name, i.number, i.cover_date, split_part(i.cover_date, '-', 2) || '/' || split_part(i.cover_date, '-', 1), p.issn)), :q) > 0)
            ORDER BY
              CASE WHEN :order = 'READING_ACTIVITY' THEN i.activity_date END DESC NULLS LAST,
              CASE WHEN :order = 'PUBLICATION_DATE' THEN i.cover_date END DESC NULLS LAST,
              CASE WHEN :order = 'PUBLICATION_DATE' AND i.number ~ '^[0-9]+$' THEN CAST(i.number AS NUMERIC) END DESC NULLS LAST,
              CASE WHEN :order = 'PUBLICATION_DATE' THEN lower(i.number) END DESC NULLS LAST,
              i.id DESC LIMIT :fetchLimit OFFSET :offset
            """.trimIndent(),
            mapOf(
                "publicationId" to publicationId,
                "status" to status?.name,
                "unread" to unread,
                "order" to order.name,
                "q" to q?.trim()?.lowercase()?.ifBlank { null },
                "fetchLimit" to limit + 1,
                "offset" to page.toLong() * limit,
            ),
        ) { rs, _ -> rs.getLong("id") }
}
