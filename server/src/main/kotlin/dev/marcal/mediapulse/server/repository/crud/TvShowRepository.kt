package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.tv.TvShow
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface TvShowRepository : CrudRepository<TvShow, Long> {
    fun findByFingerprint(fingerprint: String): TvShow?

    @Query(
        nativeQuery = true,
        value = """
            SELECT s.*
            FROM tv_shows s
            JOIN tv_show_titles st ON st.show_id = s.id
            WHERE LOWER(st.title) = LOWER(:title)
            ORDER BY st.is_primary ASC, st.id ASC
            LIMIT 1
        """,
    )
    fun findByShowTitle(title: String): TvShow?
}
