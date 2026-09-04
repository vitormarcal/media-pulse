package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.tv.ShowTermSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ShowTermAssignmentRepository(
    private val jdbc: JdbcTemplate,
) {
    fun upsert(
        showId: Long,
        termId: Long,
        source: ShowTermSource,
    ) {
        jdbc.update(
            """
            INSERT INTO show_term_assignments (show_id, term_id, source, hidden, updated_at)
            VALUES (?, ?, ?, FALSE, NOW())
            ON CONFLICT (show_id, term_id) DO UPDATE SET
              source = EXCLUDED.source, hidden = FALSE, updated_at = NOW()
            """.trimIndent(),
            showId,
            termId,
            source.name,
        )
    }

    fun updateVisibility(
        showId: Long,
        termId: Long,
        hidden: Boolean,
    ): Int =
        jdbc.update(
            "UPDATE show_term_assignments SET hidden = ?, updated_at = NOW() WHERE show_id = ? AND term_id = ?",
            hidden,
            showId,
            termId,
        )
}
