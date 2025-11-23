package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.music.AlbumHeaderRow
import dev.marcal.mediapulse.server.api.music.AlbumPageResponse
import dev.marcal.mediapulse.server.api.music.AlbumTrackRow
import dev.marcal.mediapulse.server.api.music.IdName
import dev.marcal.mediapulse.server.api.music.MusicSummaryResponse
import dev.marcal.mediapulse.server.api.music.PlaysByDayRow
import dev.marcal.mediapulse.server.api.music.RangeDto
import dev.marcal.mediapulse.server.api.music.RecentAlbumResponse
import dev.marcal.mediapulse.server.api.music.SearchAlbumRow
import dev.marcal.mediapulse.server.api.music.SearchResponse
import dev.marcal.mediapulse.server.api.music.SearchTrackRow
import dev.marcal.mediapulse.server.api.music.TopAlbumResponse
import dev.marcal.mediapulse.server.api.music.TopArtistResponse
import dev.marcal.mediapulse.server.api.music.TopTrackResponse
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.sql.Date
import java.time.Instant

@Repository
class MusicQueryRepository(
    private val entityManager: EntityManager,
) {
    fun getSummary(
        start: Instant,
        end: Instant,
    ): MusicSummaryResponse {
        val artists =
            entityManager
                .createQuery(
                    """
            SELECT COUNT(DISTINCT a.id)
            FROM TrackPlayback tp
            JOIN Track t ON t.id = tp.trackId
            JOIN Album al ON al.id = t.albumId
            JOIN Artist a ON a.id = al.artistId
            WHERE tp.playedAt BETWEEN :s AND :e
            """,
                    Long::class.java,
                ).setParameter("s", start)
                .setParameter("e", end)
                .singleResult

        val albums =
            entityManager
                .createQuery(
                    """
            SELECT COUNT(DISTINCT al.id)
            FROM TrackPlayback tp
            JOIN Track t ON t.id = tp.trackId
            JOIN Album al ON al.id = t.albumId
            WHERE tp.playedAt BETWEEN :s AND :e
            """,
                    Long::class.java,
                ).setParameter("s", start)
                .setParameter("e", end)
                .singleResult

        val tracks =
            entityManager
                .createQuery(
                    """
            SELECT COUNT(DISTINCT t.id)
            FROM TrackPlayback tp
            JOIN Track t ON t.id = tp.trackId
            WHERE tp.playedAt BETWEEN :s AND :e
            """,
                    Long::class.java,
                ).setParameter("s", start)
                .setParameter("e", end)
                .singleResult

        return MusicSummaryResponse(RangeDto(start, end), artists, albums, tracks)
    }

    fun getRecentAlbums(limit: Int): List<RecentAlbumResponse> =
        entityManager
            .createQuery(
                """
            SELECT new dev.marcal.mediapulse.server.api.music.RecentAlbumResponse(
                al.id, al.title, a.id, a.name, al.year, al.coverUrl, MAX(tp.playedAt), COUNT(tp.id)
            )
            FROM TrackPlayback tp
            JOIN Track t  ON t.id = tp.trackId
            JOIN Album al ON al.id = t.albumId
            JOIN Artist a ON a.id = al.artistId
            GROUP BY al.id, al.title, a.id, a.name, al.year, al.coverUrl
            ORDER BY MAX(tp.playedAt) DESC
            """,
                RecentAlbumResponse::class.java,
            ).setMaxResults(limit)
            .resultList

    fun getTopArtists(
        start: Instant,
        end: Instant,
        limit: Int,
    ): List<TopArtistResponse> =
        entityManager
            .createQuery(
                """
            SELECT new dev.marcal.mediapulse.server.api.music.TopArtistResponse(
                a.id, a.name, COUNT(tp.id)
            )
            FROM TrackPlayback tp
            JOIN Track t  ON t.id = tp.trackId
            JOIN Album al ON al.id = t.albumId
            JOIN Artist a ON a.id = al.artistId
            WHERE tp.playedAt BETWEEN :s AND :e
            GROUP BY a.id, a.name
            ORDER BY COUNT(tp.id) DESC
            """,
                TopArtistResponse::class.java,
            ).setParameter("s", start)
            .setParameter("e", end)
            .setMaxResults(limit)
            .resultList

    fun getTopAlbums(
        start: Instant,
        end: Instant,
        limit: Int,
    ): List<TopAlbumResponse> =
        entityManager
            .createQuery(
                """
            SELECT new dev.marcal.mediapulse.server.api.music.TopAlbumResponse(
                al.id, al.title, a.id, a.name, COUNT(tp.id)
            )
            FROM TrackPlayback tp
            JOIN Track t  ON t.id = tp.trackId
            JOIN Album al ON al.id = t.albumId
            JOIN Artist a ON a.id = al.artistId
            WHERE tp.playedAt BETWEEN :s AND :e
            GROUP BY al.id, al.title, a.id, a.name
            ORDER BY COUNT(tp.id) DESC
            """,
                TopAlbumResponse::class.java,
            ).setParameter("s", start)
            .setParameter("e", end)
            .setMaxResults(limit)
            .resultList

    fun getTopTracks(
        start: Instant,
        end: Instant,
        limit: Int,
    ): List<TopTrackResponse> =
        entityManager
            .createQuery(
                """
            SELECT new dev.marcal.mediapulse.server.api.music.TopTrackResponse(
                t.id, t.title, al.id, al.title, a.id, a.name, COUNT(tp.id)
            )
            FROM TrackPlayback tp
            JOIN Track t  ON t.id = tp.trackId
            JOIN Album al ON al.id = t.albumId
            JOIN Artist a ON a.id = al.artistId
            WHERE tp.playedAt BETWEEN :s AND :e
            GROUP BY t.id, t.title, al.id, al.title, a.id, a.name
            ORDER BY COUNT(tp.id) DESC
            """,
                TopTrackResponse::class.java,
            ).setParameter("s", start)
            .setParameter("e", end)
            .setMaxResults(limit)
            .resultList

    fun getAlbumPage(albumId: Long): AlbumPageResponse {
        // header agregado em uma passada (sem subselect)
        val header =
            entityManager
                .createQuery(
                    """
        SELECT new dev.marcal.mediapulse.server.api.music.AlbumHeaderRow(
            al.id,
            al.title,
            a.id,
            a.name,
            al.year,
            al.coverUrl,
            MAX(tp.playedAt),
            COUNT(tp.id)
        )
        FROM Album al
        JOIN Artist a ON a.id = al.artistId
        LEFT JOIN Track t ON t.albumId = al.id
        LEFT JOIN TrackPlayback tp ON tp.trackId = t.id
        WHERE al.id = :albumId
        GROUP BY al.id, al.title, a.id, a.name, al.year, al.coverUrl
        """,
                    AlbumHeaderRow::class.java,
                ).setParameter("albumId", albumId)
                .singleResult

        // tracks por álbum
        val tracks =
            entityManager
                .createQuery(
                    """
        SELECT t.id, t.title, t.discNumber, t.trackNumber,
               COUNT(tp.id) AS playCount,
               MAX(tp.playedAt) AS lastPlayed
        FROM Track t
        LEFT JOIN TrackPlayback tp ON tp.trackId = t.id
        WHERE t.albumId = :albumId
        GROUP BY t.id, t.title, t.discNumber, t.trackNumber
        ORDER BY COALESCE(t.discNumber,1), COALESCE(t.trackNumber,999), t.title
        """,
                    Array<Any>::class.java,
                ).setParameter("albumId", albumId)
                .resultList
                .map {
                    AlbumTrackRow(
                        trackId = it[0] as Long,
                        title = it[1] as String,
                        discNumber = it[2] as Int?,
                        trackNumber = it[3] as Int?,
                        playCount = (it[4] as Long?) ?: 0L,
                        lastPlayed = it[5] as Instant?,
                    )
                }

        // plays por dia
        val days =
            entityManager
                .createQuery(
                    """
    SELECT FUNCTION('date', tp.playedAt) AS day, COUNT(tp.id) AS plays
    FROM TrackPlayback tp JOIN Track t ON t.id = tp.trackId
    WHERE t.albumId = :albumId
    GROUP BY FUNCTION('date', tp.playedAt)
    ORDER BY FUNCTION('date', tp.playedAt)
    """,
                    Array<Any>::class.java,
                ).setParameter("albumId", albumId)
                .resultList
                .map {
                    PlaysByDayRow(
                        day = (it[0] as Date).toLocalDate(), // Hibernate retorna java.sql.Date
                        plays = (it[1] as Long),
                    )
                }

        return AlbumPageResponse(
            albumId = header.albumId,
            albumTitle = header.albumTitle,
            artistId = header.artistId,
            artistName = header.artistName,
            year = header.year,
            coverUrl = header.coverUrl,
            lastPlayed = header.lastPlayed,
            totalPlays = header.totalPlays,
            tracks = tracks,
            playsByDay = days,
        )
    }

    fun search(
        q: String,
        limit: Int,
    ): SearchResponse {
        val like = "%${q.lowercase()}%"

        val artists =
            entityManager
                .createQuery(
                    """SELECT a.id, a.name FROM Artist a WHERE LOWER(a.name) LIKE :q ORDER BY a.name""",
                    Array<Any>::class.java,
                ).setParameter("q", like)
                .setMaxResults(limit)
                .resultList
                .map { IdName(it[0] as Long, it[1] as String) }

        val albums =
            entityManager
                .createQuery(
                    """
            SELECT al.id, al.title, ar.name, al.year
            FROM Album al JOIN Artist ar ON ar.id = al.artistId
            WHERE LOWER(al.title) LIKE :q OR LOWER(ar.name) LIKE :q
            ORDER BY al.title
            """,
                    Array<Any>::class.java,
                ).setParameter("q", like)
                .setMaxResults(limit)
                .resultList
                .map {
                    SearchAlbumRow(it[0] as Long, it[1] as String, it[2] as String, it[3] as Int?)
                }

        val tracks =
            entityManager
                .createQuery(
                    """
            SELECT t.id, t.title, ar.name, al.title
            FROM Track t JOIN Album al ON al.id = t.albumId JOIN Artist ar ON ar.id = al.artistId
            WHERE LOWER(t.title) LIKE :q OR LOWER(ar.name) LIKE :q OR LOWER(al.title) LIKE :q
            ORDER BY t.title
            """,
                    Array<Any>::class.java,
                ).setParameter("q", like)
                .setMaxResults(limit)
                .resultList
                .map {
                    SearchTrackRow(it[0] as Long, it[1] as String, it[2] as String, it[3] as String)
                }

        return SearchResponse(artists, albums, tracks)
    }
}
