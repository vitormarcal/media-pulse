package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.AlbumTrack
import dev.marcal.mediapulse.server.model.music.AlbumTrackId
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface AlbumTrackCrudRepository : CrudRepository<AlbumTrack, AlbumTrackId> {
    /**
     * "Promote" semantics:
     * 1) If (album_id, track_id) already exists (often inserted with NULL/NULL from extended import),
     *    update it to the provided position (disc_number, track_number).
     * 2) Otherwise, insert a new row.
     * 3) If the position (album_id, disc_number, track_number) is already taken, update its track_id.
     *
     * This avoids duplicate-key on album_tracks_pkey when a NULL/NULL link exists and later we learn the position.
     */
    @Modifying
    @Transactional
    @Query(
        value = """
            WITH updated AS (
              UPDATE album_tracks
              SET disc_number = :discNumber,
                  track_number = :trackNumber
              WHERE album_id = :albumId
                AND track_id = :trackId
                AND (
                  disc_number IS DISTINCT FROM :discNumber
                  OR track_number IS DISTINCT FROM :trackNumber
                )
              RETURNING 1
            )
            INSERT INTO album_tracks(album_id, track_id, disc_number, track_number)
            SELECT :albumId, :trackId, :discNumber, :trackNumber
            WHERE NOT EXISTS (SELECT 1 FROM updated)
            ON CONFLICT (album_id, disc_number, track_number)
            WHERE disc_number IS NOT NULL AND track_number IS NOT NULL
            DO UPDATE SET
              track_id = EXCLUDED.track_id
        """,
        nativeQuery = true,
    )
    fun upsertByPosition(
        @Param("albumId") albumId: Long,
        @Param("trackId") trackId: Long,
        @Param("discNumber") discNumber: Int,
        @Param("trackNumber") trackNumber: Int,
    ): Int

    @Modifying
    @Transactional
    @Query(
        value = """
            INSERT INTO album_tracks(album_id, track_id, disc_number, track_number)
            VALUES (:albumId, :trackId, :discNumber, :trackNumber)
            ON CONFLICT (album_id, track_id)
            DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIgnoreByPk(
        @Param("albumId") albumId: Long,
        @Param("trackId") trackId: Long,
        @Param("discNumber") discNumber: Int?,
        @Param("trackNumber") trackNumber: Int?,
    ): Int
}
