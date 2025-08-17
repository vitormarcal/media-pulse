package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.controller.webhook.dto.PlexWebhookPayload
import dev.marcal.mediapulse.server.model.SourceIdentifier
import dev.marcal.mediapulse.server.model.music.MusicSource
import dev.marcal.mediapulse.server.model.music.MusicSourceIdentifier
import dev.marcal.mediapulse.server.model.music.PlaybackSource
import dev.marcal.mediapulse.server.model.music.TrackPlayback
import dev.marcal.mediapulse.server.model.plex.PlexEventType
import dev.marcal.mediapulse.server.repository.MusicAggregationRepository
import org.springframework.stereotype.Service

@Service
class PlexMusicPlaybackService(
    private val musicAggregationRepository: MusicAggregationRepository,
) {
    fun processScrobble(
        payload: PlexWebhookPayload,
        eventId: Long? = null,
    ): TrackPlayback? {
        val meta = payload.metadata

        if (meta.type != "track") return null

        val eventType = requireNotNull(PlexEventType.Companion.fromType(payload.event)) { "event type is not supported: ${payload.event}" }

        if (eventType != PlexEventType.SCROBBLE) return null

        val musicSourceIdentifiers = toIdentifiers(meta)

        val persistedMusic =
            musicAggregationRepository.findOrCreate(
                music =
                    MusicSource(
                        title = meta.title,
                        album = meta.parentTitle,
                        artist = meta.grandparentTitle,
                        year = meta.parentYear,
                    ),
                identifier = musicSourceIdentifiers,
            )

        val playback =
            TrackPlayback(
                musicSourceId = persistedMusic.id,
                source = PlaybackSource.PLEX,
                playedAt = meta.lastViewedAt,
                sourceEventId = eventId,
            )

        return musicAggregationRepository.registerPlayback(playback)
    }

    private fun toIdentifiers(meta: PlexWebhookPayload.PlexMetadata): List<MusicSourceIdentifier> =
        meta.guid.map { guid ->
            val parts = guid.id.split("://")
            require(parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                "Invalid GUID format: ${guid.id}"
            }
            val sourceIdentifier =
                SourceIdentifier.fromTag(parts[0])
                    ?: throw IllegalArgumentException("Unknown source identifier: ${parts[0]}")
            MusicSourceIdentifier(externalType = sourceIdentifier, externalId = parts[1])
        }
}
