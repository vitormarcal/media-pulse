package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.integration.plex.PlexApiClient
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.service.canonical.CanonicalizationService
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PlexArtworkService(
    private val plexApi: PlexApiClient,
    private val imageStorageService: ImageStorageService,
    private val canonical: CanonicalizationService,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun ensureAlbumCoverFromPlexThumb(
        artist: Artist,
        album: Album,
        plexThumbPath: String?,
    ) {
        if (plexThumbPath.isNullOrBlank()) return

        if (album.coverUrl != null) return

        runCatching {
            val image = plexApi.downloadImageContent(plexThumbPath)
            val localPath =
                imageStorageService.saveImageForAlbum(
                    image = image,
                    provider = "PLEX",
                    artistId = artist.id,
                    albumId = album.id,
                    fileNameHint = "${artist.name}_${album.title}",
                )
            canonical.updateAlbumCoverIfEmpty(album.id, localPath)
        }.onFailure { ex ->
            logger.warn(
                "Failed to download or store cover image for artist='${artist.name}', album='${album.title}', thumbPath='$plexThumbPath'.",
                ex,
            )
        }
    }
}
