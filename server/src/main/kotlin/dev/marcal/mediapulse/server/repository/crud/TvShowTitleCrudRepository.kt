package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.tv.TvShowTitle
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional

interface TvShowTitleCrudRepository : CrudRepository<TvShowTitle, Long> {
    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tv_show_titles(show_id, title, locale, source, is_primary)
            VALUES (:showId, :title, :locale, :source, :isPrimary)
            ON CONFLICT DO NOTHING
        """,
    )
    fun insertIgnore(
        showId: Long,
        title: String,
        locale: String?,
        source: String,
        isPrimary: Boolean,
    )
}
