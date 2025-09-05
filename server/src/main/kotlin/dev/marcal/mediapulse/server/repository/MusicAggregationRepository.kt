package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.controller.playbacksummary.dto.TrackPlaybackSummary
import dev.marcal.mediapulse.server.model.music.MusicSource
import dev.marcal.mediapulse.server.model.music.MusicSourceIdentifier
import dev.marcal.mediapulse.server.model.music.TrackPlayback
import dev.marcal.mediapulse.server.repository.crud.MusicSourceCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MusicSourceIdentifierCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TrackPlaybackCrudRepository
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class MusicAggregationRepository(
    private val trackPlaybackCrudRepository: TrackPlaybackCrudRepository,
    private val musicSourceCrudRepository: MusicSourceCrudRepository,
    private val musicSourceIdentifierCrudRepository: MusicSourceIdentifierCrudRepository,
    private val entityManager: EntityManager,
) {
    fun findOrCreate(
        music: MusicSource,
        identifier: List<MusicSourceIdentifier>,
    ): MusicSource {
        val existingMusic = musicSourceCrudRepository.findByFingerprint(music.fingerprint)
        val musicSource = existingMusic ?: musicSourceCrudRepository.save(music)

        val identifiersSaved = musicSourceIdentifierCrudRepository.findByMusicSourceId(musicSource.id)
        identifier
            .filter { id -> identifiersSaved.none { it.externalId == id.externalId } }
            .map { it.copy(musicSourceId = musicSource.id) }
            .forEach { musicSourceIdentifierCrudRepository.save(it) }

        return musicSource
    }

    fun registerPlayback(trackPlayback: TrackPlayback): TrackPlayback = trackPlaybackCrudRepository.save(trackPlayback)

    fun getPlaybackSummaryByPeriod(
        start: Instant,
        end: Instant,
        limit: Int = 100,
    ): List<TrackPlaybackSummary> {
        val results =
            entityManager
                .createQuery(
                    """
                    SELECT new dev.marcal.mediapulse.server.controller.playbacksummary.dto.TrackPlaybackSummary(
                        ct.id, ct.title, ct.album, ct.artist, ct.year, COUNT(tp.id)
                    )
                    FROM TrackPlayback tp
                    JOIN MusicSource ct ON tp.musicSourceId = ct.id
                    WHERE tp.playedAt BETWEEN :start AND :end
                    GROUP BY ct.id, ct.title, ct.album, ct.artist, ct.year
                    ORDER BY COUNT(tp.id) DESC
                    LIMIT :limit
                    """.trimIndent(),
                    TrackPlaybackSummary::class.java,
                ).setParameter("start", start)
                .setParameter("end", end)
                .setParameter("limit", limit)
                .resultList

        return results
    }
}
