package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.AlbumMusicBrainzReleaseId
import org.springframework.data.jpa.repository.JpaRepository

interface AlbumMusicBrainzReleaseIdRepository : JpaRepository<AlbumMusicBrainzReleaseId, Long> {
    fun findByReleaseId(releaseId: String): AlbumMusicBrainzReleaseId?

    fun findFirstByAlbumIdOrderByIdAsc(albumId: Long): AlbumMusicBrainzReleaseId?
}
