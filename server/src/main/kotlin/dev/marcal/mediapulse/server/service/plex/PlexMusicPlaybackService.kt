package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.controller.dto.PlexWebhookPayload
import dev.marcal.mediapulse.server.model.music.CanonicalTrack
import dev.marcal.mediapulse.server.model.music.PlaybackSource
import dev.marcal.mediapulse.server.model.music.TrackPlayback
import dev.marcal.mediapulse.server.model.plex.PlexEventType
import dev.marcal.mediapulse.server.repository.CanonicalTrackRepository
import dev.marcal.mediapulse.server.repository.TrackPlaybackRepository
import org.springframework.stereotype.Service

@Service
class PlexMusicPlaybackService(
    private val canonicalTrackRepository: CanonicalTrackRepository,
    private val trackPlaybackRepository: TrackPlaybackRepository,
) {
    companion object {
        private const val MBID_TYPE = "MBID"
        private const val MBID_PREFIX = "mbid://"
    }

    /**
     * Processes a scrobble event from Plex.
     *
     * @param payload The payload containing the scrobble event data.
     * @param eventId Optional event ID for tracking purposes.
     * @return The saved TrackPlayback entity, or null if the event type is not SCROBBLE or metadata type is not "track".
     */
    fun processScrobble(
        payload: PlexWebhookPayload,
        eventId: Long? = null,
    ): TrackPlayback? {
        val meta = payload.metadata

        if (meta.type != "track") {
            return null
        }

        val eventType = requireNotNull(PlexEventType.Companion.fromType(payload.event)) { "event type is not supported: ${payload.event}" }

        if (eventType != PlexEventType.SCROBBLE) {
            return null
        }

        val mbidRaw =
            meta.guid
                .map { it.id }
                .firstOrNull { it.startsWith(MBID_PREFIX) }
                ?: throw IllegalArgumentException("MBID missing in  payload")

        val mbid =
            mbidRaw.removePrefix(MBID_PREFIX).takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("MBID is empty or malformed: $mbidRaw")

        val canonicalTrack =
            canonicalTrackRepository.findByCanonicalIdAndCanonicalType(mbid, MBID_TYPE)
                ?: canonicalTrackRepository.save(
                    CanonicalTrack(
                        canonicalId = mbid,
                        canonicalType = MBID_TYPE,
                        title = meta.title,
                        album = meta.parentTitle,
                        artist = meta.grandparentTitle,
                    ),
                )

        val playback =
            TrackPlayback(
                canonicalTrackId = canonicalTrack.id,
                source = PlaybackSource.PLEX,
                playedAt = meta.lastViewedAt,
                sourceEventId = eventId,
            )

        return trackPlaybackRepository.save(playback)
    }
}
