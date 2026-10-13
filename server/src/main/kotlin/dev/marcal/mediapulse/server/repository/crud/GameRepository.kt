package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.game.Game
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface GameRepository : CrudRepository<Game, Long> {
    @Modifying
    @Query(nativeQuery = true, value = "UPDATE games SET cover_url = :url, updated_at = NOW() WHERE id = :id")
    fun updateCover(
        id: Long,
        url: String,
    ): Int

    fun findByFingerprint(fingerprint: String): Game?

    fun findBySlug(slug: String): Game?

    fun findByIgdbId(igdbId: String): Game?

    fun findBySteamGridDbId(steamGridDbId: String): Game?

    @Query(
        nativeQuery = true,
        value = """
            SELECT g.*
            FROM games g
            WHERE LOWER(g.title) = LOWER(:title)
              AND g.year IS NOT DISTINCT FROM :year
            ORDER BY g.id ASC
            LIMIT 1
        """,
    )
    fun findByTitleAndYear(
        title: String,
        year: Int?,
    ): Game?
}
