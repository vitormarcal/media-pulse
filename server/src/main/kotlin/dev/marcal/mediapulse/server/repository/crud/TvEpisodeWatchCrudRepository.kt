package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.tv.TvEpisodeWatch
import dev.marcal.mediapulse.server.model.tv.TvEpisodeWatchSource
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface TvEpisodeWatchCrudRepository : CrudRepository<TvEpisodeWatch, Long> {
    fun existsByEpisodeIdAndSourceAndWatchedAt(
        episodeId: Long,
        source: TvEpisodeWatchSource,
        watchedAt: Instant,
    ): Boolean

    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO tv_episode_watches(episode_id, source, watched_at)
            VALUES (:episodeId, :source, :watchedAt)
            ON CONFLICT DO NOTHING
        """,
    )
    fun insertIgnore(
        episodeId: Long,
        source: String,
        watchedAt: Instant,
    )
}
