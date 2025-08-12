package dev.marcal.mediapulse.server.repository

import org.springframework.stereotype.Repository

@Repository
class TrackPlaybackRepository(
    private val trackPlaybackCrudRepository: dev.marcal.mediapulse.server.repository.crud.TrackPlaybackCrudRepository,
) {
    fun save(
        trackPlayback: dev.marcal.mediapulse.server.model.music.TrackPlayback,
    ): dev.marcal.mediapulse.server.model.music.TrackPlayback = trackPlaybackCrudRepository.save(trackPlayback)
}
