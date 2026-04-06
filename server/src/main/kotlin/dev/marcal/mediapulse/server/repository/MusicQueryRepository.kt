package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.music.AlbumCoverageResponse
import dev.marcal.mediapulse.server.api.music.AlbumHeaderRow
import dev.marcal.mediapulse.server.api.music.AlbumPageResponse
import dev.marcal.mediapulse.server.api.music.AlbumTrackRow
import dev.marcal.mediapulse.server.api.music.ArtistAlbumRow
import dev.marcal.mediapulse.server.api.music.ArtistCoverageResponse
import dev.marcal.mediapulse.server.api.music.ArtistPageResponse
import dev.marcal.mediapulse.server.api.music.ArtistTrackRow
import dev.marcal.mediapulse.server.api.music.IdName
import dev.marcal.mediapulse.server.api.music.MusicSummaryResponse
import dev.marcal.mediapulse.server.api.music.PlaysByDayRow
import dev.marcal.mediapulse.server.api.music.RangeDto
import dev.marcal.mediapulse.server.api.music.RecentAlbumResponse
import dev.marcal.mediapulse.server.api.music.RecentAlbumsPageResponse
import dev.marcal.mediapulse.server.api.music.RecentGenreResponse
import dev.marcal.mediapulse.server.api.music.SearchAlbumRow
import dev.marcal.mediapulse.server.api.music.SearchResponse
import dev.marcal.mediapulse.server.api.music.SearchTrackRow
import dev.marcal.mediapulse.server.api.music.TopAlbumResponse
import dev.marcal.mediapulse.server.api.music.TopArtistResponse
import dev.marcal.mediapulse.server.api.music.TopGenreBySourceResponse
import dev.marcal.mediapulse.server.api.music.TopGenreResponse
import dev.marcal.mediapulse.server.api.music.TopTrackResponse
import dev.marcal.mediapulse.server.api.music.TrackAlbumRow
import dev.marcal.mediapulse.server.api.music.TrackPageResponse
import dev.marcal.mediapulse.server.api.music.TrackPlayRow
import dev.marcal.mediapulse.server.api.music.TrendingGenreResponse
import dev.marcal.mediapulse.server.api.music.UnderplayedGenreResponse
import jakarta.persistence.EntityManager
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Repository
import org.springframework.web.server.ResponseStatusException
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant

@Repository
class MusicQueryRepository(
    private val entityManager: EntityManager,
) {
    // Summary and tops
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
            JOIN Album al ON al.id = tp.albumId
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
            JOIN Album al ON al.id = tp.albumId
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

    fun getRecentAlbums(
        limit: Int,
        cursor: String?,
    ): RecentAlbumsPageResponse {
        val resolvedLimit = limit.coerceAtLeast(1)
        val (cursorLastPlayed, cursorAlbumId) = parseRecentAlbumCursor(cursor)
        val rows =
            entityManager
                .createNativeQuery(
                    """
                    WITH recent_albums AS (
                      SELECT
                        al.id AS album_id,
                        al.title AS album_title,
                        a.id AS artist_id,
                        a.name AS artist_name,
                        al.year AS album_year,
                        al.cover_url,
                        MAX(tp.played_at) AS last_played,
                        COUNT(tp.id) AS play_count
                      FROM track_playbacks tp
                      JOIN albums al ON al.id = tp.album_id
                      JOIN artists a ON a.id = al.artist_id
                      GROUP BY al.id, al.title, a.id, a.name, al.year, al.cover_url
                    )
                    SELECT
                      album_id,
                      album_title,
                      artist_id,
                      artist_name,
                      album_year,
                      cover_url,
                      last_played,
                      play_count
                    FROM recent_albums
                    WHERE (
                      :cursorLastPlayed IS NULL
                      OR last_played < :cursorLastPlayed
                      OR (last_played = :cursorLastPlayed AND album_id < :cursorAlbumId)
                    )
                    ORDER BY last_played DESC, album_id DESC
                    LIMIT :limitPlusOne
                    """.trimIndent(),
                ).setParameter("cursorLastPlayed", cursorLastPlayed)
                .setParameter("cursorAlbumId", cursorAlbumId)
                .setParameter("limitPlusOne", resolvedLimit + 1)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    RecentAlbumResponse(
                        albumId = (fields[0] as Number).toLong(),
                        albumTitle = fields[1] as String,
                        artistId = (fields[2] as Number).toLong(),
                        artistName = fields[3] as String,
                        year = (fields[4] as Number?)?.toInt(),
                        coverUrl = fields[5] as String?,
                        lastPlayed = asInstant(fields[6])!!,
                        playCount = (fields[7] as Number).toLong(),
                    )
                }
        val hasMore = rows.size > resolvedLimit
        val items = rows.take(resolvedLimit)
        val nextCursor = items.lastOrNull()?.let { buildRecentAlbumCursor(it.lastPlayed, it.albumId) }
        return RecentAlbumsPageResponse(
            items = items,
            nextCursor = if (hasMore) nextCursor else null,
        )
    }

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
            JOIN Album al ON al.id = tp.albumId
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
            JOIN Album al ON al.id = tp.albumId
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
                t.id, t.title,
                al.id, al.title,
                a.id, a.name,
                COUNT(tp.id)
            )
            FROM TrackPlayback tp
            JOIN Track t ON t.id = tp.trackId
            JOIN Album al ON al.id = tp.albumId
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

    fun getTopGenres(
        start: Instant,
        end: Instant,
        limit: Int,
    ): List<TopGenreResponse> =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  g.name AS genre,
                  COUNT(tp.id) AS play_count
                FROM track_playbacks tp
                JOIN albums al       ON al.id = tp.album_id
                JOIN album_genres ag ON ag.album_id = al.id
                JOIN genres g        ON g.id = ag.genre_id
                WHERE tp.played_at BETWEEN :s AND :e
                GROUP BY g.name
                ORDER BY COUNT(tp.id) DESC
                """.trimIndent(),
            ).setParameter("s", start)
            .setParameter("e", end)
            .setMaxResults(limit)
            .resultList
            .map {
                val row = it as Array<*>
                TopGenreResponse(
                    genre = row[0] as String,
                    playCount = (row[1] as Number).toLong(),
                )
            }

    // Discovery
    fun getNeverPlayedAlbums(limit: Int): List<TopAlbumResponse> =
        entityManager
            .createQuery(
                """
            SELECT new dev.marcal.mediapulse.server.api.music.TopAlbumResponse(
                al.id,
                al.title,
                a.id,
                a.name,
                0L
            )
            FROM Album al
            JOIN Artist a ON a.id = al.artistId
            LEFT JOIN TrackPlayback tp ON tp.albumId = al.id
            GROUP BY al.id, al.title, a.id, a.name
            HAVING COUNT(tp.id) = 0
            ORDER BY a.name, al.year, al.title
            """,
                TopAlbumResponse::class.java,
            ).setMaxResults(limit)
            .resultList

    fun getArtistCoverage(limit: Int): List<ArtistCoverageResponse> =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  a.id AS artist_id,
                  a.name AS artist_name,
                  COUNT(DISTINCT at.track_id) AS total_tracks,
                  COUNT(DISTINCT CASE WHEN tp.id IS NOT NULL THEN at.track_id END) AS played_tracks,
                  CASE
                    WHEN COUNT(DISTINCT at.track_id) = 0 THEN 0.0
                    ELSE (COUNT(DISTINCT CASE WHEN tp.id IS NOT NULL THEN at.track_id END) * 100.0 / COUNT(DISTINCT at.track_id))
                  END AS coverage_percent
                FROM artists a
                JOIN albums al ON al.artist_id = a.id
                LEFT JOIN album_tracks at ON at.album_id = al.id
                LEFT JOIN track_playbacks tp
                  ON tp.album_id = al.id
                 AND tp.track_id = at.track_id
                GROUP BY a.id, a.name
                ORDER BY coverage_percent ASC, total_tracks DESC
                LIMIT :n
                """.trimIndent(),
            ).setParameter("n", limit)
            .resultList
            .map {
                val row = it as Array<*>
                ArtistCoverageResponse(
                    artistId = (row[0] as Number).toLong(),
                    artistName = row[1] as String,
                    totalTracks = (row[2] as Number).toLong(),
                    playedTracks = (row[3] as Number).toLong(),
                    coveragePercent = (row[4] as Number).toDouble(),
                )
            }

    fun getAlbumCoverage(limit: Int): List<AlbumCoverageResponse> =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  al.id AS album_id,
                  al.title AS album_title,
                  a.id AS artist_id,
                  a.name AS artist_name,
                  COUNT(DISTINCT at.track_id) AS total_tracks,
                  COUNT(DISTINCT CASE WHEN tp.id IS NOT NULL THEN at.track_id END) AS played_tracks,
                  CASE
                    WHEN COUNT(DISTINCT at.track_id) = 0 THEN 0.0
                    ELSE (COUNT(DISTINCT CASE WHEN tp.id IS NOT NULL THEN at.track_id END) * 100.0 / COUNT(DISTINCT at.track_id))
                  END AS coverage_percent
                FROM albums al
                JOIN artists a ON a.id = al.artist_id
                LEFT JOIN album_tracks at ON at.album_id = al.id
                LEFT JOIN track_playbacks tp
                  ON tp.album_id = al.id
                 AND tp.track_id = at.track_id
                GROUP BY al.id, al.title, a.id, a.name
                ORDER BY coverage_percent ASC, total_tracks DESC
                LIMIT :n
                """.trimIndent(),
            ).setParameter("n", limit)
            .resultList
            .map {
                val row = it as Array<*>
                AlbumCoverageResponse(
                    albumId = (row[0] as Number).toLong(),
                    albumTitle = row[1] as String,
                    artistId = (row[2] as Number).toLong(),
                    artistName = row[3] as String,
                    totalTracks = (row[4] as Number).toLong(),
                    playedTracks = (row[5] as Number).toLong(),
                    coveragePercent = (row[6] as Number).toDouble(),
                )
            }

    // Album details
    fun getAlbumPage(albumId: Long): AlbumPageResponse {
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
        LEFT JOIN TrackPlayback tp ON tp.albumId = al.id
        WHERE al.id = :albumId
        GROUP BY al.id, al.title, a.id, a.name, al.year, al.coverUrl
        """,
                    AlbumHeaderRow::class.java,
                ).setParameter("albumId", albumId)
                .singleResult

        val tracks =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      t.id AS track_id,
                      t.title AS title,
                      at.disc_number AS disc_number,
                      at.track_number AS track_number,
                      COUNT(tp.id) AS play_count,
                      MAX(tp.played_at) AS last_played
                    FROM album_tracks at
                    JOIN tracks t ON t.id = at.track_id
                    LEFT JOIN track_playbacks tp
                      ON tp.album_id = at.album_id
                     AND tp.track_id = at.track_id
                    WHERE at.album_id = :albumId
                    GROUP BY t.id, t.title, at.disc_number, at.track_number
                    ORDER BY COALESCE(at.disc_number, 1), COALESCE(at.track_number, 999), t.title
                    """.trimIndent(),
                ).setParameter("albumId", albumId)
                .resultList
                .map {
                    val row = it as Array<*>
                    AlbumTrackRow(
                        trackId = (row[0] as Number).toLong(),
                        title = row[1] as String,
                        discNumber = row[2] as Int?,
                        trackNumber = row[3] as Int?,
                        playCount = (row[4] as Number).toLong(),
                        lastPlayed = row[5] as Instant?,
                    )
                }

        val days =
            entityManager
                .createQuery(
                    """
    SELECT FUNCTION('date', tp.playedAt) AS day, COUNT(tp.id) AS plays
    FROM TrackPlayback tp
    WHERE tp.albumId = :albumId
    GROUP BY FUNCTION('date', tp.playedAt)
    ORDER BY FUNCTION('date', tp.playedAt)
    """,
                    Array<Any>::class.java,
                ).setParameter("albumId", albumId)
                .resultList
                .map {
                    PlaysByDayRow(
                        day = (it[0] as Date).toLocalDate(),
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

    fun getArtistPage(artistId: Long): ArtistPageResponse {
        val summaryRow =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      a.id,
                      a.name,
                      (SELECT COUNT(*) FROM track_playbacks tp JOIN tracks t ON t.id = tp.track_id WHERE t.artist_id = a.id) AS total_plays,
                      (SELECT COUNT(DISTINCT tp.track_id) FROM track_playbacks tp JOIN tracks t ON t.id = tp.track_id WHERE t.artist_id = a.id) AS unique_tracks_played,
                      (SELECT COUNT(DISTINCT tp.album_id) FROM track_playbacks tp JOIN tracks t ON t.id = tp.track_id WHERE t.artist_id = a.id) AS unique_albums_played,
                      (SELECT COUNT(*) FROM albums al WHERE al.artist_id = a.id) AS library_albums_count,
                      (SELECT COUNT(*) FROM tracks t WHERE t.artist_id = a.id) AS library_tracks_count,
                      (SELECT MAX(tp.played_at) FROM track_playbacks tp JOIN tracks t ON t.id = tp.track_id WHERE t.artist_id = a.id) AS last_played
                    FROM artists a
                    WHERE a.id = :artistId
                    """.trimIndent(),
                ).setParameter("artistId", artistId)
                .resultList
                .firstOrNull() as Array<*>?
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "artista não encontrado")

        val albums =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      al.id,
                      al.title,
                      al.year,
                      al.cover_url,
                      COUNT(DISTINCT at.track_id) AS total_tracks,
                      COUNT(DISTINCT CASE WHEN tp.id IS NOT NULL THEN at.track_id END) AS played_tracks,
                      COUNT(tp.id) AS play_count,
                      MAX(tp.played_at) AS last_played
                    FROM albums al
                    LEFT JOIN album_tracks at ON at.album_id = al.id
                    LEFT JOIN track_playbacks tp ON tp.album_id = al.id
                    WHERE al.artist_id = :artistId
                    GROUP BY al.id, al.title, al.year, al.cover_url
                    ORDER BY COUNT(tp.id) DESC, MAX(tp.played_at) DESC NULLS LAST, al.year DESC NULLS LAST, al.title
                    """.trimIndent(),
                ).setParameter("artistId", artistId)
                .resultList
                .map {
                    val row = it as Array<*>
                    ArtistAlbumRow(
                        albumId = (row[0] as Number).toLong(),
                        albumTitle = row[1] as String,
                        year = row[2] as Int?,
                        coverUrl = row[3] as String?,
                        totalTracks = (row[4] as Number).toLong(),
                        playedTracks = (row[5] as Number).toLong(),
                        playCount = (row[6] as Number).toLong(),
                        lastPlayed = row[7] as Instant?,
                    )
                }

        val topTracks =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      t.id,
                      t.title,
                      top_album.album_id,
                      top_album.album_title,
                      COUNT(tp.id) AS play_count,
                      MAX(tp.played_at) AS last_played
                    FROM tracks t
                    LEFT JOIN track_playbacks tp ON tp.track_id = t.id
                    LEFT JOIN LATERAL (
                      SELECT al2.id AS album_id, al2.title AS album_title
                      FROM track_playbacks tp2
                      JOIN albums al2 ON al2.id = tp2.album_id
                      WHERE tp2.track_id = t.id
                      GROUP BY al2.id, al2.title
                      ORDER BY COUNT(*) DESC, MAX(tp2.played_at) DESC
                      LIMIT 1
                    ) top_album ON TRUE
                    WHERE t.artist_id = :artistId
                    GROUP BY t.id, t.title, top_album.album_id, top_album.album_title
                    ORDER BY COUNT(tp.id) DESC, MAX(tp.played_at) DESC NULLS LAST, t.title
                    LIMIT 12
                    """.trimIndent(),
                ).setParameter("artistId", artistId)
                .resultList
                .map {
                    val row = it as Array<*>
                    ArtistTrackRow(
                        trackId = (row[0] as Number).toLong(),
                        title = row[1] as String,
                        albumId = (row[2] as Number?)?.toLong(),
                        albumTitle = row[3] as String?,
                        playCount = (row[4] as Number).toLong(),
                        lastPlayed = row[5] as Instant?,
                    )
                }

        val days =
            entityManager
                .createQuery(
                    """
                    SELECT FUNCTION('date', tp.playedAt) AS day, COUNT(tp.id) AS plays
                    FROM TrackPlayback tp
                    JOIN Track t ON t.id = tp.trackId
                    WHERE t.artistId = :artistId
                    GROUP BY FUNCTION('date', tp.playedAt)
                    ORDER BY FUNCTION('date', tp.playedAt)
                    """,
                    Array<Any>::class.java,
                ).setParameter("artistId", artistId)
                .resultList
                .map {
                    PlaysByDayRow(
                        day = (it[0] as Date).toLocalDate(),
                        plays = (it[1] as Long),
                    )
                }

        return ArtistPageResponse(
            artistId = (summaryRow[0] as Number).toLong(),
            artistName = summaryRow[1] as String,
            totalPlays = (summaryRow[2] as Number).toLong(),
            uniqueTracksPlayed = (summaryRow[3] as Number).toLong(),
            uniqueAlbumsPlayed = (summaryRow[4] as Number).toLong(),
            libraryAlbumsCount = (summaryRow[5] as Number).toLong(),
            libraryTracksCount = (summaryRow[6] as Number).toLong(),
            lastPlayed = summaryRow[7] as Instant?,
            albums = albums,
            topTracks = topTracks,
            playsByDay = days,
        )
    }

    fun getTrackPage(trackId: Long): TrackPageResponse {
        val header =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      t.id,
                      t.title,
                      a.id,
                      a.name,
                      COUNT(tp.id) AS total_plays,
                      MAX(tp.played_at) AS last_played
                    FROM tracks t
                    JOIN artists a ON a.id = t.artist_id
                    LEFT JOIN track_playbacks tp ON tp.track_id = t.id
                    WHERE t.id = :trackId
                    GROUP BY t.id, t.title, a.id, a.name
                    """.trimIndent(),
                ).setParameter("trackId", trackId)
                .resultList
                .firstOrNull() as Array<*>?
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "faixa não encontrada")

        val albums =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      al.id,
                      al.title,
                      al.year,
                      al.cover_url,
                      at.disc_number,
                      at.track_number,
                      COUNT(tp.id) AS play_count,
                      MAX(tp.played_at) AS last_played
                    FROM album_tracks at
                    JOIN albums al ON al.id = at.album_id
                    LEFT JOIN track_playbacks tp
                      ON tp.track_id = at.track_id
                     AND tp.album_id = at.album_id
                    WHERE at.track_id = :trackId
                    GROUP BY al.id, al.title, al.year, al.cover_url, at.disc_number, at.track_number
                    ORDER BY COUNT(tp.id) DESC, MAX(tp.played_at) DESC NULLS LAST, al.year DESC NULLS LAST, al.title
                    """.trimIndent(),
                ).setParameter("trackId", trackId)
                .resultList
                .map {
                    val row = it as Array<*>
                    TrackAlbumRow(
                        albumId = (row[0] as Number).toLong(),
                        albumTitle = row[1] as String,
                        year = row[2] as Int?,
                        coverUrl = row[3] as String?,
                        discNumber = row[4] as Int?,
                        trackNumber = row[5] as Int?,
                        playCount = (row[6] as Number).toLong(),
                        lastPlayed = row[7] as Instant?,
                    )
                }

        val recentPlays =
            entityManager
                .createNativeQuery(
                    """
                    SELECT tp.played_at, tp.source, al.id, al.title
                    FROM track_playbacks tp
                    JOIN albums al ON al.id = tp.album_id
                    WHERE tp.track_id = :trackId
                    ORDER BY tp.played_at DESC
                    LIMIT 24
                    """.trimIndent(),
                ).setParameter("trackId", trackId)
                .resultList
                .map {
                    val row = it as Array<*>
                    TrackPlayRow(
                        playedAt = row[0] as Instant,
                        source = row[1] as String,
                        albumId = (row[2] as Number).toLong(),
                        albumTitle = row[3] as String,
                    )
                }

        return TrackPageResponse(
            trackId = (header[0] as Number).toLong(),
            title = header[1] as String,
            artistId = (header[2] as Number).toLong(),
            artistName = header[3] as String,
            totalPlays = (header[4] as Number).toLong(),
            lastPlayed = header[5] as Instant?,
            albums = albums,
            recentPlays = recentPlays,
        )
    }

    // Search
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

        // tracks: agora Track tem artistId, mas não tem albumId.
        // Mostrar "albumTitle" aqui é mais caro: precisa escolher um álbum (ex: o mais ouvido).
        // Solução simples: pega o álbum mais tocado do track (ou qualquer um), via query nativa.
        val tracks =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      t.id AS track_id,
                      t.title AS track_title,
                      a.name AS artist_name,
                      COALESCE(al.title, '') AS album_title
                    FROM tracks t
                    JOIN artists a ON a.id = t.artist_id
                    LEFT JOIN LATERAL (
                      SELECT al2.title
                      FROM track_playbacks tp2
                      JOIN albums al2 ON al2.id = tp2.album_id
                      WHERE tp2.track_id = t.id
                      GROUP BY al2.title
                      ORDER BY COUNT(*) DESC, MAX(tp2.played_at) DESC
                      LIMIT 1
                    ) al ON TRUE
                    WHERE LOWER(t.title) LIKE :q OR LOWER(a.name) LIKE :q
                    ORDER BY t.title
                    LIMIT :n
                    """.trimIndent(),
                ).setParameter("q", like)
                .setParameter("n", limit)
                .resultList
                .map {
                    val row = it as Array<*>
                    SearchTrackRow(
                        row[0] as Long,
                        row[1] as String,
                        row[2] as String,
                        row[3] as String,
                    )
                }

        return SearchResponse(artists, albums, tracks)
    }

    // genres
    fun getTrendingGenres(
        start: Instant,
        end: Instant,
        compareStart: Instant,
        compareEnd: Instant,
        limit: Int,
    ): List<TrendingGenreResponse> =
        entityManager
            .createNativeQuery(
                """
                WITH now_counts AS (
                  SELECT
                    g.name AS genre,
                    COUNT(tp.id) AS play_count
                  FROM track_playbacks tp
                  JOIN albums al ON al.id = tp.album_id
                  JOIN album_genres ag ON ag.album_id = al.id
                  JOIN genres g ON g.id = ag.genre_id
                  WHERE tp.played_at BETWEEN :s AND :e
                  GROUP BY g.name
                ),
                prev_counts AS (
                  SELECT
                    g.name AS genre,
                    COUNT(tp.id) AS play_count
                  FROM track_playbacks tp
                  JOIN albums al ON al.id = tp.album_id
                  JOIN album_genres ag ON ag.album_id = al.id
                  JOIN genres g ON g.id = ag.genre_id
                  WHERE tp.played_at BETWEEN :ps AND :pe
                  GROUP BY g.name
                )
                SELECT
                  COALESCE(n.genre, p.genre) AS genre,
                  COALESCE(n.play_count, 0)  AS play_count_now,
                  COALESCE(p.play_count, 0)  AS play_count_prev,
                  (COALESCE(n.play_count, 0) - COALESCE(p.play_count, 0)) AS delta
                FROM now_counts n
                FULL OUTER JOIN prev_counts p ON p.genre = n.genre
                ORDER BY delta DESC, play_count_now DESC, genre ASC
                LIMIT :n
                """.trimIndent(),
            ).setParameter("s", start)
            .setParameter("e", end)
            .setParameter("ps", compareStart)
            .setParameter("pe", compareEnd)
            .setParameter("n", limit)
            .resultList
            .map {
                val row = it as Array<*>
                TrendingGenreResponse(
                    genre = row[0] as String,
                    playCountNow = (row[1] as Number).toLong(),
                    playCountPrev = (row[2] as Number).toLong(),
                    delta = (row[3] as Number).toLong(),
                )
            }

    fun getRecentGenres(limit: Int): List<RecentGenreResponse> =
        entityManager
            .createNativeQuery(
                """
                WITH recent_plays AS (
                  SELECT tp.id, tp.album_id, tp.played_at
                  FROM track_playbacks tp
                  ORDER BY tp.played_at DESC
                  LIMIT :n
                )
                SELECT
                  g.name AS genre,
                  MAX(rp.played_at) AS last_played,
                  COUNT(*) AS play_count_in_window
                FROM recent_plays rp
                JOIN album_genres ag ON ag.album_id = rp.album_id
                JOIN genres g ON g.id = ag.genre_id
                GROUP BY g.name
                ORDER BY MAX(rp.played_at) DESC
                """.trimIndent(),
            ).setParameter("n", limit)
            .resultList
            .map {
                val row = it as Array<*>
                RecentGenreResponse(
                    genre = row[0] as String,
                    lastPlayed = row[1] as Instant,
                    playCountInWindow = (row[2] as Number).toLong(),
                )
            }

    fun getUnderplayedGenres(
        start: Instant,
        end: Instant,
        minLibraryAlbums: Int,
        limit: Int,
    ): List<UnderplayedGenreResponse> =
        entityManager
            .createNativeQuery(
                """
                SELECT
                  g.name AS genre,
                  COUNT(DISTINCT ag.album_id) AS library_albums,
                  COUNT(tp.id) AS play_count,
                  MAX(tp.played_at) AS last_played
                FROM album_genres ag
                JOIN genres g ON g.id = ag.genre_id
                LEFT JOIN track_playbacks tp
                  ON tp.album_id = ag.album_id
                 AND tp.played_at BETWEEN :s AND :e
                GROUP BY g.name
                HAVING COUNT(DISTINCT ag.album_id) >= :minAlbums
                ORDER BY
                  COUNT(tp.id) ASC,
                  MAX(tp.played_at) NULLS FIRST,
                  COUNT(DISTINCT ag.album_id) DESC,
                  g.name ASC
                LIMIT :n
                """.trimIndent(),
            ).setParameter("s", start)
            .setParameter("e", end)
            .setParameter("minAlbums", minLibraryAlbums)
            .setParameter("n", limit)
            .resultList
            .map {
                val row = it as Array<*>
                UnderplayedGenreResponse(
                    genre = row[0] as String,
                    libraryAlbums = (row[1] as Number).toLong(),
                    playCount = (row[2] as Number).toLong(),
                    lastPlayed = row[3] as Instant?,
                )
            }

    fun getTopGenresBySource(
        start: Instant,
        end: Instant,
        limit: Int,
    ): List<TopGenreBySourceResponse> {
        val rows =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      ags.source AS source,
                      g.name AS genre,
                      COUNT(tp.id) AS play_count
                    FROM track_playbacks tp
                    JOIN albums al ON al.id = tp.album_id
                    JOIN album_genre_sources ags ON ags.album_id = al.id
                    JOIN genres g ON g.id = ags.genre_id
                    WHERE tp.played_at BETWEEN :s AND :e
                    GROUP BY ags.source, g.name
                    ORDER BY ags.source ASC, COUNT(tp.id) DESC, g.name ASC
                    """.trimIndent(),
                ).setParameter("s", start)
                .setParameter("e", end)
                .resultList
                .map {
                    val row = it as Array<*>
                    Triple(
                        row[0] as String,
                        row[1] as String,
                        (row[2] as Number).toLong(),
                    )
                }

        val grouped =
            rows
                .groupBy({ it.first }, { TopGenreResponse(it.second, it.third) })
                .mapValues { (_, list) -> list.take(limit) }

        return grouped.entries
            .sortedBy { it.key }
            .map { (source, genres) -> TopGenreBySourceResponse(source = source, genres = genres) }
    }

    private fun asInstant(value: Any?): Instant? =
        when (value) {
            null -> null
            is Instant -> value
            is Timestamp -> value.toInstant()
            is java.util.Date -> value.toInstant()
            else -> null
        }

    private fun parseRecentAlbumCursor(cursor: String?): Pair<Instant?, Long?> {
        if (cursor.isNullOrBlank()) return null to null
        val parts = cursor.split(":")
        require(parts.size == 4 && parts[0] == "ts" && parts[2] == "id") { "Invalid cursor format." }
        val lastPlayed = Instant.ofEpochMilli(parts[1].toLongOrNull() ?: error("Invalid cursor value."))
        val albumId = parts[3].toLongOrNull() ?: error("Invalid cursor value.")
        return lastPlayed to albumId
    }

    private fun buildRecentAlbumCursor(
        lastPlayed: Instant,
        albumId: Long,
    ): String = "ts:${lastPlayed.toEpochMilli()}:id:$albumId"
}
