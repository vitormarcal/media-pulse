package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.Track
import org.springframework.data.repository.CrudRepository

interface TrackRepository : CrudRepository<Track, Long> {
    fun findByFingerprint(fingerprint: String): Track?

    fun findByAlbumIdAndDiscNumberAndTrackNumber(
        albumId: Long,
        discNumber: Int,
        trackNumber: Int,
    ): Track?

    fun findAllByAlbumIdAndTitle(
        albumId: Long,
        title: String,
    ): List<Track>
}
