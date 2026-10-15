package dev.marcal.mediapulse.server.repository.query

import dev.marcal.mediapulse.server.api.music.AlbumArtistDto
import dev.marcal.mediapulse.server.model.music.AlbumArtistSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class AlbumArtistQueryRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun findByAlbumIds(albumIds: Collection<Long>): Map<Long, List<AlbumArtistDto>> {
        if (albumIds.isEmpty()) return emptyMap()
        val rows =
            jdbc.query(
                """SELECT aa.album_id, aa.artist_id, a.name, aa.position, aa.credited_name, aa.join_phrase, aa.source
               FROM album_artists aa JOIN artists a ON a.id = aa.artist_id
               WHERE aa.album_id IN (:albumIds)
               UNION ALL
               SELECT al.id, a.id, a.name, 0, a.name, '', 'LEGACY'
               FROM albums al JOIN artists a ON a.id = al.artist_id
               WHERE al.id IN (:albumIds) AND NOT EXISTS (SELECT 1 FROM album_artists aa WHERE aa.album_id = al.id)
               ORDER BY 1, 4""",
                mapOf("albumIds" to albumIds),
            ) { rs, _ ->
                rs.getLong(1) to
                    AlbumArtistDto(
                        rs.getLong(2),
                        rs.getString(3),
                        rs.getInt(4),
                        rs.getString(5),
                        rs.getString(6),
                        AlbumArtistSource.valueOf(rs.getString(7)),
                    )
            }
        return rows.groupBy({ it.first }, { it.second })
    }
}
