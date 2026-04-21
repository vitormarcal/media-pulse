package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.tv.TvEpisode
import org.springframework.data.repository.CrudRepository

interface TvEpisodeRepository : CrudRepository<TvEpisode, Long> {
    fun findByFingerprint(fingerprint: String): TvEpisode?

    fun findByShowIdAndSeasonNumberAndEpisodeNumber(
        showId: Long,
        seasonNumber: Int?,
        episodeNumber: Int?,
    ): TvEpisode?

    fun findByShowIdAndSeasonNumberOrderByEpisodeNumberAscIdAsc(
        showId: Long,
        seasonNumber: Int?,
    ): List<TvEpisode>
}
