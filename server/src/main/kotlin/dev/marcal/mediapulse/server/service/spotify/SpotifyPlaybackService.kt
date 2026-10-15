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

        val album = track.album
        val trackArtist = track.artists?.firstOrNull { !it.name.isNullOrBlank() }
        val albumArtist = album?.artists?.firstOrNull { !it.name.isNullOrBlank() }
        val resolvedTrackArtist = trackArtist ?: albumArtist
        val resolvedAlbumArtist = albumArtist ?: trackArtist

        val albumTitle = album?.name ?: "Unknown"
        val albumSpotifyId = album?.id
        val albumYear = album?.releaseDate?.take(4)?.toIntOrNull()
        val coverUrl = album?.images?.maxByOrNull { it.width ?: 0 }?.url

        val trackTitle = track.name ?: return
        val trackSpotifyId = track.id
        val trackNumber = track.trackNumber
        val discNumber = track.discNumber
        val durationMs = track.durationMs

        val trackArtistEntity =
            canonical.ensureArtist(
                name = resolvedTrackArtist?.name ?: "Unknown",
                spotifyId = resolvedTrackArtist?.id,
                musicbrainzId = null,
            )
        val albumArtistEntity =
            if (resolvedAlbumArtist == resolvedTrackArtist) {
                trackArtistEntity
            } else {
                canonical.ensureArtist(
                    name = resolvedAlbumArtist?.name ?: "Unknown",
                    spotifyId = resolvedAlbumArtist?.id,
                    musicbrainzId = null,
                )
            }

        val albumEntity =
            canonical.ensureAlbum(
                artist = albumArtistEntity,
                title = albumTitle,
                year = albumYear,
                coverUrl = null,
                spotifyId = albumSpotifyId,
            )

        val trackEntity =
            canonical.ensureTrackInAlbum(
                album = albumEntity,
                artist = trackArtistEntity,
                title = trackTitle,
                durationMs = durationMs,
                discNumber = discNumber,
                trackNumber = trackNumber,
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
            artist = albumArtistEntity,
            album = albumEntity,
            spotifyImageUrl = coverUrl,
        )
    }
}
