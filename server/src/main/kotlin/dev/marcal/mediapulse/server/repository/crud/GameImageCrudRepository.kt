package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.game.GameImage
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional

interface GameImageCrudRepository : CrudRepository<GameImage, Long> {
    fun existsByGameIdAndIsPrimaryTrue(gameId: Long): Boolean

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO game_images(game_id, url, kind, is_primary)
            VALUES (:gameId, :url, :kind, :isPrimary)
            ON CONFLICT DO NOTHING
        """,
    )
    fun insertIgnore(
        gameId: Long,
        url: String,
        kind: String,
        isPrimary: Boolean,
    )

    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            SELECT id
            FROM games
            WHERE id = :gameId
            FOR UPDATE
        """,
    )
    fun lockGameRowForPrimaryUpdate(gameId: Long): Long?

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            UPDATE game_images
            SET is_primary = FALSE
            WHERE game_id = :gameId
              AND is_primary = TRUE
        """,
    )
    fun clearPrimaryForGame(gameId: Long): Int

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            UPDATE game_images
            SET is_primary = TRUE
            WHERE game_id = :gameId
              AND url = :url
        """,
    )
    fun markPrimaryForGame(
        gameId: Long,
        url: String,
    ): Int
}
