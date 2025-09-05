package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.model.series.EpisodePlayback
import dev.marcal.mediapulse.server.model.series.EpisodeSource
import dev.marcal.mediapulse.server.model.series.EpisodeSourceIdentifier
import dev.marcal.mediapulse.server.repository.crud.EpisodePlaybackCrudRepository
import dev.marcal.mediapulse.server.repository.crud.EpisodeSourceCrudRepository
import dev.marcal.mediapulse.server.repository.crud.EpisodeSourceIdentifierCrudRepository
import org.springframework.stereotype.Repository

@Repository
class SeriesAggregationRepository(
    private val episodeSourceCrud: EpisodeSourceCrudRepository,
    private val episodeSourceIdentifierCrud: EpisodeSourceIdentifierCrudRepository,
    private val episodePlaybackCrud: EpisodePlaybackCrudRepository,
) {
    fun findOrCreate(
        episode: EpisodeSource,
        identifiers: List<EpisodeSourceIdentifier>,
    ): EpisodeSource {
        val existingEpisode = episodeSourceCrud.findByFingerprint(episode.fingerprint)
        val episodeSource = existingEpisode ?: episodeSourceCrud.save(episode)

        val identifiersSaved = episodeSourceIdentifierCrud.findByEpisodeSourceId(episodeSource.id)
        identifiers
            .filter { id -> identifiersSaved.none { it.externalType == id.externalType && it.externalId == id.externalId } }
            .forEach { episodeSourceIdentifierCrud.save(it.copy(episodeSourceId = episodeSource.id)) }

        return episodeSource
    }

    fun registerPlayback(episodePlayback: EpisodePlayback): EpisodePlayback = episodePlaybackCrud.save(episodePlayback)
}
