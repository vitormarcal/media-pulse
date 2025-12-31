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
            INSERT INTO album_tracks(album_id, track_id, disc_number, track_number)
            VALUES (:albumId, :trackId, :discNumber, :trackNumber)
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
