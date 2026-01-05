package dev.marcal.mediapulse.server.service.spotify

import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyRecentlyPlayedItem
import dev.marcal.mediapulse.server.model.music.PlaybackSource
import dev.marcal.mediapulse.server.repository.crud.TrackPlaybackCrudRepository
import dev.marcal.mediapulse.server.service.canonical.CanonicalizationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class SpotifyPlaybackService(
    private val canonical: CanonicalizationService,
    private val trackPlaybackRepo: TrackPlaybackCrudRepository,
    private val spotifyArtworkService: SpotifyArtworkService,
) {
    @Transactional
    suspend fun processRecentlyPlayedItem(
        item: SpotifyRecentlyPlayedItem,
        eventId: Long?,
    ) {
        val track = item.track ?: return
        val playedAt = Instant.parse(item.playedAt)

        val mainArtist =
            track.artists?.firstOrNull()
                ?: track.album?.artists?.firstOrNull()

        val artistName = mainArtist?.name ?: "Unknown"
        val artistSpotifyId = mainArtist?.id

        val album = track.album
        val albumTitle = album?.name ?: "Unknown"
        val albumSpotifyId = album?.id
        val albumYear = album?.releaseDate?.take(4)?.toIntOrNull()
        val coverUrl = album?.images?.maxByOrNull { it.width ?: 0 }?.url

        val trackTitle = track.name ?: return
        val trackSpotifyId = track.id
        val trackNumber = track.trackNumber
        val discNumber = track.discNumber
        val durationMs = track.durationMs

        val artistEntity =
            canonical.ensureArtist(
                name = artistName,
                spotifyId = artistSpotifyId,
                musicbrainzId = null,
            )

        val albumEntity =
            canonical.ensureAlbum(
                artist = artistEntity,
                title = albumTitle,
                year = albumYear,
                coverUrl = null,
                spotifyId = albumSpotifyId,
            )

        val trackEntity =
            canonical.ensureTrack(
                artist = artistEntity,
                title = trackTitle,
                durationMs = durationMs,
                spotifyId = trackSpotifyId,
            )

        canonical.linkTrackToAlbum(
            album = albumEntity,
            track = trackEntity,
            discNumber = discNumber,
            trackNumber = trackNumber,
        )

        trackPlaybackRepo.insertIgnore(
            trackId = trackEntity.id,
            albumId = albumEntity.id, // NEW
            source = PlaybackSource.SPOTIFY.name,
            sourceEventId = eventId,
            playedAt = playedAt,
        )

        spotifyArtworkService.ensureAlbumCoverFromSpotifyUrl(
            artist = artistEntity,
            album = albumEntity,
            spotifyImageUrl = coverUrl,
        )
    }
}
