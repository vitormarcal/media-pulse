package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.TrackPlayback
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface TrackPlaybackCrudRepository : CrudRepository<TrackPlayback, Long> {
    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO track_playbacks(track_id, album_id, source, source_event_id, played_at)
            VALUES (:trackId, :albumId, :source, :sourceEventId, :playedAt)
            ON CONFLICT DO NOTHING
        """,
    )
    fun insertIgnore(
        trackId: Long,
        albumId: Long,
        source: String,
        sourceEventId: Long?,
        playedAt: Instant,
    )
}
