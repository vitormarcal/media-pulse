package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.TrackPlayback
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface TrackPlaybackCrudRepository : JpaRepository<TrackPlayback, Long> {
    @Modifying
    @Query(
        value = """
        INSERT INTO track_playbacks (track_id, source, source_event_id, played_at, created_at)
        VALUES (:trackId, :source, :sourceEventId, :playedAt, NOW())
        ON CONFLICT (source, track_id, played_at) DO NOTHING
    """,
        nativeQuery = true,
    )
    fun insertIgnore(
        @Param("trackId") trackId: Long,
        @Param("source") source: String,
        @Param("sourceEventId") sourceEventId: Long?,
        @Param("playedAt") playedAt: Instant,
    )
}
