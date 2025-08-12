package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.model.music.CanonicalTrack
import dev.marcal.mediapulse.server.repository.crud.CanonicalTrackCrudRepository
import org.springframework.stereotype.Repository

@Repository
class CanonicalTrackRepository(
    private val canonicalTrackCrudRepository: CanonicalTrackCrudRepository,
) {
    fun findOrCreate(track: CanonicalTrack): CanonicalTrack =
        canonicalTrackCrudRepository.findByCanonicalIdAndCanonicalType(track.canonicalId, track.canonicalType)
            ?: canonicalTrackCrudRepository.save(track)
}
