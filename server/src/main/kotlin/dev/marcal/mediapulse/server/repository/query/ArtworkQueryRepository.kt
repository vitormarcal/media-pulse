package dev.marcal.mediapulse.server.repository.query

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ArtworkQueryRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun ids(
        q: String?,
        artistId: Long?,
        type: String?,
        movement: String?,
        favorite: Boolean?,
        listId: Long?,
        page: Int,
        limit: Int,
    ): List<Long> =
        jdbc.query(
            """
            SELECT a.id FROM artworks a LEFT JOIN artwork_artists artist ON artist.id = a.artist_id
            WHERE (CAST(:q AS TEXT) IS NULL
              OR strpos(lower(concat_ws(' ', a.title, artist.name, a.movement, a.technique, a.description)), :q) > 0)
              AND (CAST(:artist AS BIGINT) IS NULL OR a.artist_id = :artist)
              AND (CAST(:type AS TEXT) IS NULL OR a.type = :type)
              AND (CAST(:movement AS TEXT) IS NULL OR a.movement = :movement)
              AND (CAST(:favorite AS BOOLEAN) IS NULL OR a.favorite = :favorite)
              AND (CAST(:list AS BIGINT) IS NULL OR EXISTS
                (SELECT 1 FROM artwork_list_items i WHERE i.artwork_id = a.id AND i.list_id = :list))
            ORDER BY a.id DESC LIMIT :limit OFFSET :offset
            """.trimIndent(),
            mapOf(
                "q" to q?.trim()?.lowercase()?.ifBlank { null },
                "artist" to artistId,
                "type" to type,
                "movement" to movement,
                "favorite" to favorite,
                "list" to listId,
                "limit" to limit + 1,
                "offset" to page.toLong() * limit,
            ),
        ) { rs, _ -> rs.getLong("id") }

    fun filters(): Map<String, List<String>> =
        mapOf(
            "types" to
                jdbc.jdbcTemplate.queryForList(
                    "SELECT DISTINCT type FROM artworks WHERE type IS NOT NULL ORDER BY type",
                    String::class.java,
                ),
            "movements" to
                jdbc.jdbcTemplate.queryForList(
                    "SELECT DISTINCT movement FROM artworks WHERE movement IS NOT NULL ORDER BY movement",
                    String::class.java,
                ),
        )
}
