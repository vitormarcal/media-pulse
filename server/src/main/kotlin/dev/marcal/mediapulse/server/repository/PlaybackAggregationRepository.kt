package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.controller.dto.TrackPlaybackSummary
import dev.marcal.mediapulse.server.model.music.CanonicalTrack
import dev.marcal.mediapulse.server.model.music.TrackPlayback
import dev.marcal.mediapulse.server.repository.crud.CanonicalTrackCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TrackPlaybackCrudRepository
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class PlaybackAggregationRepository(
    private val trackPlaybackCrudRepository: TrackPlaybackCrudRepository,
    private val canonicalTrackCrudRepository: CanonicalTrackCrudRepository,
    private val entityManager: EntityManager,
) {
    fun findOrCreate(track: CanonicalTrack): CanonicalTrack =
        canonicalTrackCrudRepository.findByCanonicalIdAndCanonicalType(track.canonicalId, track.canonicalType)
            ?: canonicalTrackCrudRepository.save(track)

    fun registerPlayback(trackPlayback: TrackPlayback): TrackPlayback = trackPlaybackCrudRepository.save(trackPlayback)

    fun getPlaybackSummaryByPeriod(
        start: Instant,
        end: Instant,
    ): List<TrackPlaybackSummary> {
        val results =
            entityManager
                .createQuery(
                    """
                    SELECT new dev.marcal.mediapulse.server.controller.dto.TrackPlaybackSummary(
                        ct.id, ct.title, ct.album, ct.artist, ct.year, COUNT(tp.id)
                    )
                    FROM TrackPlayback tp
                    JOIN CanonicalTrack ct ON tp.canonicalTrackId = ct.id
                    WHERE tp.playedAt BETWEEN :start AND :end
                    GROUP BY ct.id, ct.title, ct.album, ct.artist, ct.year
                    """.trimIndent(),
                    TrackPlaybackSummary::class.java,
                ).setParameter("start", start)
                .setParameter("end", end)
                .resultList

        return results
    }
}
