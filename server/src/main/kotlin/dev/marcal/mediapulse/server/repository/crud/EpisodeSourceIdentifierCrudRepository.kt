package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.series.EpisodeSourceIdentifier
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EpisodeSourceIdentifierCrudRepository : JpaRepository<EpisodeSourceIdentifier, Long> {
    fun findByEpisodeSourceId(episodeSourceId: Long): List<EpisodeSourceIdentifier>
}
