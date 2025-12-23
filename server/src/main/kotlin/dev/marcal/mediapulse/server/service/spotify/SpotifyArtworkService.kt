package dev.marcal.mediapulse.server.service.spotify

import dev.marcal.mediapulse.server.integration.spotify.SpotifyImageClient
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.service.canonical.CanonicalizationService
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SpotifyArtworkService(
    private val spotifyImageClient: SpotifyImageClient,
    private val imageStorageService: ImageStorageService,
    private val canonical: CanonicalizationService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun ensureAlbumCoverFromSpotifyUrl(
        artist: Artist,
        album: Album,
        spotifyImageUrl: String?,
    ) {
        if (spotifyImageUrl.isNullOrBlank()) return
        if (album.coverUrl != null) return

        runCatching {
            val image = spotifyImageClient.downloadImage(spotifyImageUrl)

            val localPath =
                imageStorageService.saveImageForAlbum(
                    image = image,
                    provider = "SPOTIFY",
                    artistId = artist.id,
                    albumId = album.id,
                    fileNameHint = "${artist.name}_${album.title}",
                )

            canonical.updateAlbumCoverIfEmpty(album.id, localPath)
        }.onFailure { ex ->
            logger.warn(
                "Failed to download/store Spotify cover. artist='{}' album='{}' url='{}'",
                artist.name,
                album.title,
                spotifyImageUrl,
                ex,
            )
        }
    }
}
