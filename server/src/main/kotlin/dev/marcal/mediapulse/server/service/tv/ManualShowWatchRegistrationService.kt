package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.model.tv.TvEpisodeWatchSource
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeWatchCrudRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ManualShowWatchRegistrationService(
    private val tvEpisodeWatchCrudRepository: TvEpisodeWatchCrudRepository,
) {
    fun register(
        episodeId: Long,
        watchedAt: Instant,
    ): Boolean {
        val alreadyExists =
            tvEpisodeWatchCrudRepository.existsByEpisodeIdAndSourceAndWatchedAt(
                episodeId = episodeId,
                source = TvEpisodeWatchSource.MANUAL,
                watchedAt = watchedAt,
            )

        tvEpisodeWatchCrudRepository.insertIgnore(
            episodeId = episodeId,
            source = TvEpisodeWatchSource.MANUAL.name,
            watchedAt = watchedAt,
        )

        return !alreadyExists
    }
}
