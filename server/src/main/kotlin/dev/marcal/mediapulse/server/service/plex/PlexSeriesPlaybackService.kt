package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.controller.webhook.dto.PlexWebhookPayload
import dev.marcal.mediapulse.server.model.PlexSourceIdentifierParser
import dev.marcal.mediapulse.server.model.music.PlaybackSource
import dev.marcal.mediapulse.server.model.plex.PlexEventType
import dev.marcal.mediapulse.server.model.series.EpisodePlayback
import dev.marcal.mediapulse.server.model.series.EpisodeSource
import dev.marcal.mediapulse.server.repository.SeriesAggregationRepository
import org.springframework.stereotype.Service

@Service
class PlexSeriesPlaybackService(
    private val seriesAggregationRepository: SeriesAggregationRepository,
) {
    fun processScrobble(
        payload: PlexWebhookPayload,
        eventId: Long? = null,
    ): EpisodePlayback? {
        val meta = payload.metadata

        val eventType = requireNotNull(PlexEventType.fromType(payload.event)) { "Unsupported event: ${payload.event}" }

        if (meta.type != "episode" || eventType != PlexEventType.SCROBBLE) return null

        val persistedEpisode =
            seriesAggregationRepository.findOrCreate(
                episode =
                    EpisodeSource(
                        showTitle = meta.grandparentTitle,
                        seasonNumber = meta.parentIndex,
                        episodeNumber = meta.index,
                        episodeTitle = meta.title,
                        year = meta.year,
                    ),
                identifiers = PlexSourceIdentifierParser.toEpisodeIdentifiers(meta),
            )

        val playback =
            EpisodePlayback(
                episodeSourceId = persistedEpisode.id,
                source = PlaybackSource.PLEX,
                playedAt = meta.lastViewedAt,
                sourceEventId = eventId,
            )

        return seriesAggregationRepository.registerPlayback(playback)
    }
}
