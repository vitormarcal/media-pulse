package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.tv.TvShowImage
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional

interface TvShowImageCrudRepository : CrudRepository<TvShowImage, Long> {
    fun existsByShowIdAndIsPrimaryTrue(showId: Long): Boolean

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tv_show_images(show_id, url, is_primary)
            VALUES (:showId, :url, :isPrimary)
            ON CONFLICT DO NOTHING
        """,
    )
    fun insertIgnore(
        showId: Long,
        url: String,
        isPrimary: Boolean,
    )

    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            SELECT id
            FROM tv_shows
            WHERE id = :showId
            FOR UPDATE
        """,
    )
    fun lockShowRowForPrimaryUpdate(showId: Long): Long?

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            UPDATE tv_show_images
            SET is_primary = FALSE
            WHERE show_id = :showId
              AND is_primary = TRUE
        """,
    )
    fun clearPrimaryForShow(showId: Long): Int

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            UPDATE tv_show_images
            SET is_primary = TRUE
            WHERE show_id = :showId
              AND url = :url
        """,
    )
    fun markPrimaryForShow(
        showId: Long,
        url: String,
    ): Int
}
