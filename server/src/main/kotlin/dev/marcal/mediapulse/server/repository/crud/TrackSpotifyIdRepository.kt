package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.TrackSpotifyId
import org.springframework.data.jpa.repository.JpaRepository

interface TrackSpotifyIdRepository : JpaRepository<TrackSpotifyId, Long> {
    fun findBySpotifyId(spotifyId: String): TrackSpotifyId?
}
