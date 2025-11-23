package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.integration.plex.PlexApiClient
import dev.marcal.mediapulse.server.integration.plex.dto.PlexLibrarySection
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.service.canonical.CanonicalizationService
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import dev.marcal.mediapulse.server.util.PlexGuidUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PlexImportService(
    private val plexApi: PlexApiClient,
    private val imageStorageService: ImageStorageService,
    private val canonical: CanonicalizationService,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    data class ImportStats(
        var artistsSeen: Int = 0,
        var artistsUpserted: Int = 0,
        var albumsSeen: Int = 0,
        var albumsUpserted: Int = 0,
    )

    suspend fun importAllArtistsAndAlbums(
        sectionKey: String? = null,
        pageSize: Int = 200,
    ): ImportStats {
        val stats = ImportStats()

        logger.info(
            "Starting Plex library import. sectionKey={}, pageSize={}",
            sectionKey ?: "<all-sections>",
            pageSize,
        )

        val sections =
            if (sectionKey != null) {
                listOf(PlexLibrarySection(key = sectionKey, type = "artist"))
            } else {
                plexApi.listMusicSections()
            }

        for (section in sections) {
            var start = 0
            var total = Int.MAX_VALUE
            while (start < total) {
                val (artists, tot) = plexApi.listArtistsPaged(section.key, start, pageSize)
                total = tot
                if (artists.isEmpty()) break

                for (a in artists) {
                    stats.artistsSeen++
                    val mbidArtist = PlexGuidUtil.firstValue(a.guids, "mbid")
                    val plexArtistGuid = PlexGuidUtil.firstValue(a.guids, "plex")?.let { "plex://$it" }
                    val artist =
                        canonical.ensureArtist(
                            name = a.title,
                            musicbrainzId = mbidArtist,
                            plexGuid = plexArtistGuid,
                            spotifyId = null,
                        )
                    stats.artistsUpserted++

                    importAlbumsForArtist(section.key, a.ratingKey, artist, stats, pageSize)
                }

                start += artists.size
            }
        }

        logger.info(
            "Finished Plex library import. artistsSeen={}, artistsUpserted={}, albumsSeen={}, albumsUpserted={}",
            stats.artistsSeen,
            stats.artistsUpserted,
            stats.albumsSeen,
            stats.albumsUpserted,
        )

        return stats
    }

    private suspend fun importAlbumsForArtist(
        sectionKey: String,
        artistRatingKey: String,
        artist: Artist,
        stats: ImportStats,
        pageSize: Int,
    ) {
        var start = 0
        var total = Int.MAX_VALUE
        while (start < total) {
            val (albums, tot) = plexApi.listAlbumsByArtistPaged(sectionKey, artistRatingKey, start, pageSize)
            total = tot
            if (albums.isEmpty()) break

            for (al in albums) {
                stats.albumsSeen++

                val mbidAlbum = PlexGuidUtil.firstValue(al.guids, "mbid")
                val plexAlbumGuid = PlexGuidUtil.firstValue(al.guids, "plex")?.let { "plex://$it" }

                val album =
                    canonical.ensureAlbum(
                        artist = artist,
                        title = al.title,
                        year = al.year,
                        coverUrl = null,
                        musicbrainzId = mbidAlbum,
                        plexGuid = plexAlbumGuid,
                        spotifyId = null,
                    )

                if (album.coverUrl == null && !al.thumb.isNullOrBlank()) {
                    runCatching {
                        val image = plexApi.downloadImageContent(al.thumb)
                        val localPath =
                            imageStorageService.saveImageForAlbum(
                                image = image,
                                provider = "PLEX",
                                artistId = artist.id,
                                albumId = album.id,
                                fileNameHint = "${artist.name}_${al.title}",
                            )
                        canonical.updateAlbumCoverIfEmpty(album.id, localPath)
                    }.onFailure { ex ->
                        logger.warn(
                            "Failed to download or store cover image for artist='${artist.name}', album='${al.title}', thumbPath='${al.thumb}'.",
                            ex,
                        )
                    }
                }

                stats.albumsUpserted++
            }

            start += albums.size
        }
    }
}
