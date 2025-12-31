package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.controller.webhook.dto.PlexWebhookPayload
import dev.marcal.mediapulse.server.model.music.PlaybackSource
import dev.marcal.mediapulse.server.model.music.TrackPlayback
import dev.marcal.mediapulse.server.model.plex.PlexEventType
import dev.marcal.mediapulse.server.repository.crud.EventSourceCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TrackPlaybackCrudRepository
import dev.marcal.mediapulse.server.service.canonical.CanonicalizationService
import dev.marcal.mediapulse.server.service.plex.util.PlexGuidExtractor
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class PlexMusicPlaybackService(
    private val canonical: CanonicalizationService,
    private val trackPlaybackRepo: TrackPlaybackCrudRepository,
    private val plexArtworkService: PlexArtworkService,
    private val eventSourceCrudRepository: EventSourceCrudRepository,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Transactional
    suspend fun processScrobble(
        payload: PlexWebhookPayload,
        eventId: Long? = null,
    ): TrackPlayback? {
        val meta = payload.metadata

        if (meta.type != "track") return null

        val eventType = requireNotNull(PlexEventType.fromType(payload.event)) { "event type is not supported: ${payload.event}" }

        if (eventType != PlexEventType.SCROBBLE) return null

        // Metadados canônicos
        val artistName = meta.grandparentTitle.orEmpty()
        val albumTitle = meta.parentTitle.orEmpty()
        val albumYear = meta.parentYear
        val coverUrl = meta.parentThumb
        val trackTitle = meta.title
        val trackNumber = meta.index
        val discNumber = meta.parentIndex

        val playedAt = meta.lastViewedAt ?: eventId?.let { eventSourceCrudRepository.findByIdOrNull(it) }?.createdAt ?: Instant.now()

        val guids = PlexGuidExtractor.extractGuids(meta)
        val mbidTrack = guids["mbid"]

        // Futuro: se vierem MBIDs de álbum/artista num array específico, passe aqui:
        val mbidAlbum: String? = null
        val mbidArtist: String? = null

        // 1) Artist
        val artist =
            canonical.ensureArtist(
                name = artistName,
                musicbrainzId = mbidArtist,
                spotifyId = null,
            )

        // 2) Album
        val album =
            canonical.ensureAlbum(
                artist = artist,
                title = albumTitle,
                year = albumYear,
                coverUrl = null,
                musicbrainzId = mbidAlbum,
                spotifyId = null,
            )

        plexArtworkService.ensureAlbumCoverFromPlexThumb(
            artist = artist,
            album = album,
            plexThumbPath = coverUrl,
        )

        // 3) Track
        val track =
            canonical.ensureTrack(
                artist = artist,
                title = trackTitle,
                durationMs = null,
                musicbrainzId = mbidTrack,
            )

        canonical.linkTrackToAlbum(
            album = album,
            track = track,
            discNumber = discNumber,
            trackNumber = trackNumber,
        )

        // 4) Playback
        val playback =
            TrackPlayback(
                trackId = track.id,
                albumId = album.id, // NEW
                source = PlaybackSource.PLEX,
                sourceEventId = eventId,
                playedAt = playedAt,
            )

        trackPlaybackRepo.insertIgnore(
            trackId = track.id,
            albumId = album.id, // NEW
            source = PlaybackSource.PLEX.name,
            sourceEventId = eventId,
            playedAt = playedAt,
        )
        logger.info("Playback registrado: trackId=${track.id}, playedAt=$playedAt, event=$eventId")
        return playback
    }
}
