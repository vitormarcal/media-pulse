package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.AlbumSpotifyId
import org.springframework.data.jpa.repository.JpaRepository

interface AlbumSpotifyIdRepository : JpaRepository<AlbumSpotifyId, Long> {
    fun findBySpotifyId(spotifyId: String): AlbumSpotifyId?
}
