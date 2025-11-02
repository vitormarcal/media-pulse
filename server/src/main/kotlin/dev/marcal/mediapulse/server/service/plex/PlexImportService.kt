package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.integration.plex.PlexApiClient
import dev.marcal.mediapulse.server.integration.plex.dto.PlexLibrarySection
import dev.marcal.mediapulse.server.service.canonical.CanonicalizationService
import dev.marcal.mediapulse.server.util.PlexGuidUtil
import org.springframework.stereotype.Service

@Service
class PlexImportService(
    private val plexApi: PlexApiClient,
    private val canonical: CanonicalizationService,
) {

    data class ImportStats(
        var artistsSeen: Int = 0,
        var artistsUpserted: Int = 0,
        var albumsSeen: Int = 0,
        var albumsUpserted: Int = 0
    )

    suspend fun importAllArtistsAndAlbums(
        auth: PlexApiClient.PlexAuth,
        sectionKey: String? = null,
        pageSize: Int = 200
    ): ImportStats {
        val stats = ImportStats()

        val sections = if (sectionKey != null) {
            listOf(PlexLibrarySection(key = sectionKey, type = "artist"))
        } else {
            plexApi.listMusicSections(auth)
        }

        for (section in sections) {
            // 1) Lista artistas paginados
            var start = 0
            var total = Int.MAX_VALUE
            while (start < total) {
                val (artists, tot) = plexApi.listArtistsPaged(auth, section.key, start, pageSize)
                total = tot
                if (artists.isEmpty()) break

                for (a in artists) {
                    stats.artistsSeen++
                    val mbidArtist = PlexGuidUtil.firstValue(a.guids, "mbid")
                    val plexArtistGuid = PlexGuidUtil.firstValue(a.guids, "plex")?.let { "plex://$it" }
                    val artist = canonical.ensureArtist(
                        name = a.title,
                        musicbrainzId = mbidArtist,
                        plexGuid = plexArtistGuid,
                        spotifyId = null
                    )
                    stats.artistsUpserted++

                    // 2) Para cada artista, lista álbuns
                    importAlbumsForArtist(auth, section.key, a.ratingKey, a.title, artist.id, stats, pageSize)
                }

                start += artists.size
            }
        }

        return stats
    }

    private suspend fun importAlbumsForArtist(
        auth: PlexApiClient.PlexAuth,
        sectionKey: String,
        artistRatingKey: String,
        artistName: String,
        artistId: Long,
        stats: ImportStats,
        pageSize: Int
    ) {
        var start = 0
        var total = Int.MAX_VALUE
        while (start < total) {
            val (albums, tot) = plexApi.listAlbumsByArtistPaged(auth, sectionKey, artistRatingKey, start, pageSize)
            total = tot
            if (albums.isEmpty()) break

            for (al in albums) {
                stats.albumsSeen++
                val mbidAlbum = PlexGuidUtil.firstValue(al.guids, "mbid")
                val plexAlbumGuid = PlexGuidUtil.firstValue(al.guids, "plex")?.let { "plex://$it" }
                // O Plex às vezes manda parentTitle=artista. Usamos o artista já canônico.
                val album = canonical.ensureAlbum(
                    artist = dev.marcal.mediapulse.server.model.music.Artist(
                        id = artistId,
                        name = artistName,
                        fingerprint = "avoid-use-ctor" // não será salvo; apenas referência
                    ),
                    title = al.title,
                    year = al.year,
                    coverUrl = al.thumb,
                    musicbrainzId = mbidAlbum,
                    plexGuid = plexAlbumGuid,
                    spotifyId = null
                )
                stats.albumsUpserted++
            }

            start += albums.size
        }
    }
}
