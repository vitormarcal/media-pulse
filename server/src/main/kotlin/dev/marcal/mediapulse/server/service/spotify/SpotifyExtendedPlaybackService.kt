package dev.marcal.mediapulse.server.service.spotify

import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyExtendedHistoryItem
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.model.music.PlaybackSource
import dev.marcal.mediapulse.server.model.music.Track
import dev.marcal.mediapulse.server.repository.crud.TrackPlaybackCrudRepository
import dev.marcal.mediapulse.server.service.canonical.CanonicalizationService
import dev.marcal.mediapulse.server.util.TitleKeyUtil
import dev.marcal.mediapulse.server.util.TxUtil
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class SpotifyExtendedPlaybackService(
    private val canonical: CanonicalizationService,
    private val trackPlaybackRepo: TrackPlaybackCrudRepository,
    private val tx: TxUtil,
    private val em: EntityManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun processChunk(
        items: List<SpotifyExtendedHistoryItem>,
        eventId: Long?,
    ) {
        val artistCache = HashMap<String, Artist>(200)
        val albumCache = HashMap<String, Album>(500)
        val trackCache = HashMap<String, Track>(2000)
        tx.inTx {
            var i = 0
            for (item in items) {
                processExtendedItem(item, eventId, artistCache, albumCache, trackCache)
                i++

                if (i % 100 == 0) {
                    em.flush()
                    em.clear()

                    artistCache.clear()
                    albumCache.clear()
                    trackCache.clear()
                }
            }
            em.flush()
            em.clear()
        }
    }

    fun processExtendedItem(
        item: SpotifyExtendedHistoryItem,
        eventId: Long?,
        artistCache: MutableMap<String, Artist>,
        albumCache: MutableMap<String, Album>,
        trackCache: MutableMap<String, Track>,
    ) {
        val ts = item.ts ?: return
        val playedAt = runCatching { Instant.parse(ts) }.getOrNull() ?: return

        val trackTitle = item.trackName?.takeIf { it.isNotBlank() } ?: return
        val artistName = item.artistName?.takeIf { it.isNotBlank() } ?: "Unknown"
        val albumTitle = item.albumName?.takeIf { it.isNotBlank() } ?: "Unknown"

        val artistEntity =
            artistCache.getOrPut(artistName) {
                canonical.ensureArtist(name = artistName)
            }

        val albumKey = artistEntity.id.toString() + "|" + TitleKeyUtil.albumTitleKey(albumTitle).ifBlank { "unknown" }

        val albumEntity =
            albumCache.getOrPut(albumKey) {
                canonical.ensureAlbum(artist = artistEntity, title = albumTitle, year = null, coverUrl = null)
            }

        val trackSpotifyId = extractTrackId(item.spotifyTrackUri)

        val trackKey = trackSpotifyId ?: ("t:$trackTitle")
        val trackEntity =
            trackCache.getOrPut(trackKey) {
                canonical.ensureTrack(
                    artist = artistEntity,
                    title = trackTitle,
                    durationMs = null,
                    spotifyId = trackSpotifyId,
                )
            }

        canonical.linkTrackToAlbum(
            album = albumEntity,
            track = trackEntity,
            discNumber = null,
            trackNumber = null,
        )

        runCatching {
            trackPlaybackRepo.insertIgnore(
                trackId = trackEntity.id,
                albumId = albumEntity.id,
                source = PlaybackSource.SPOTIFY.name,
                sourceEventId = eventId,
                playedAt = playedAt,
            )
        }.onFailure { ex ->
            logger.debug("Playback insert skipped (likely duplicate) | playedAt={} trackId={}", playedAt, trackEntity.id)
        }
    }

    private fun extractTrackId(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        // spotify:track:<id>
        val parts = uri.split(":")
        if (parts.size == 3 && parts[0] == "spotify" && parts[1] == "track") return parts[2].ifBlank { null }
        return null
    }
}
