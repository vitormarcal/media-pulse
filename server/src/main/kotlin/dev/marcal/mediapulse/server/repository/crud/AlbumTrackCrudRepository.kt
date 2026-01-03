package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.AlbumTrack
import dev.marcal.mediapulse.server.model.music.AlbumTrackId
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param

interface AlbumTrackCrudRepository : CrudRepository<AlbumTrack, AlbumTrackId> {
    @Modifying
    @Query(
        value = """
        WITH _lock AS (
          SELECT pg_advisory_xact_lock(:albumId)
        ),
        evict AS (
          UPDATE album_tracks
          SET disc_number = NULL,
              track_number = NULL
          WHERE album_id = :albumId
            AND disc_number = :discNumber
            AND track_number = :trackNumber
            AND track_id <> :trackId
          RETURNING 1
        ),
        upd AS (
          UPDATE album_tracks
          SET disc_number = :discNumber,
              track_number = :trackNumber
          WHERE album_id = :albumId
            AND track_id = :trackId
          RETURNING 1
        )
        INSERT INTO album_tracks(album_id, track_id, disc_number, track_number)
        SELECT :albumId, :trackId, :discNumber, :trackNumber
        WHERE NOT EXISTS (SELECT 1 FROM upd)
        ON CONFLICT (album_id, track_id)
        DO UPDATE SET
          disc_number = EXCLUDED.disc_number,
          track_number = EXCLUDED.track_number
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
