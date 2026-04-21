package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.Track
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param

interface TrackRepository : CrudRepository<Track, Long> {
    fun findByFingerprint(fingerprint: String): Track?

    @Query(
        value = """
            SELECT t.*
            FROM tracks t
            JOIN album_tracks at ON at.track_id = t.id
            WHERE at.album_id = :albumId
              AND at.disc_number = :discNumber
              AND at.track_number = :trackNumber
            ORDER BY t.id ASC
            LIMIT 1
        """,
        nativeQuery = true,
    )
    fun findFirstByAlbumPosition(
        @Param("albumId") albumId: Long,
        @Param("discNumber") discNumber: Int,
        @Param("trackNumber") trackNumber: Int,
    ): Track?

    @Query(
        value = """
            SELECT DISTINCT t.*
            FROM tracks t
            JOIN album_tracks at ON at.track_id = t.id
            WHERE at.album_id = :albumId
              AND t.artist_id = :artistId
            ORDER BY t.id ASC
        """,
        nativeQuery = true,
    )
    fun findAllByAlbumAndArtist(
        @Param("albumId") albumId: Long,
        @Param("artistId") artistId: Long,
    ): List<Track>
}
