package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.MusicSourceIdentifier
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MusicSourceIdentifierCrudRepository : JpaRepository<MusicSourceIdentifier, Long> {
    fun findByMusicSourceId(musicSourceId: Long): List<MusicSourceIdentifier>
}
