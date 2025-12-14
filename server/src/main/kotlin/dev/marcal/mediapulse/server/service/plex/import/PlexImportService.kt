package dev.marcal.mediapulse.server.service.plex.import

import dev.marcal.mediapulse.server.integration.plex.PlexApiClient
import dev.marcal.mediapulse.server.integration.plex.dto.PlexLibrarySection
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.service.canonical.CanonicalizationService
import dev.marcal.mediapulse.server.service.music.AlbumGenreService
import dev.marcal.mediapulse.server.service.plex.PlexArtworkService
import dev.marcal.mediapulse.server.service.plex.util.PlexGuidUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PlexImportService(
    private val plexApi: PlexApiClient,
    private val canonical: CanonicalizationService,
    private val plexArtworkService: PlexArtworkService,
    private val albumGenreService: AlbumGenreService,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    data class ImportStats(
        var artistsSeen: Int = 0,
        var artistsUpserted: Int = 0,
        var albumsSeen: Int = 0,
        var albumsUpserted: Int = 0,
        var tracksSeen: Int = 0,
        var tracksUpserted: Int = 0,
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
            "Finished Plex library import. artistsSeen={}, artistsUpserted={}, albumsSeen={}, albumsUpserted={}, tracksSeen={}, tracksUpserted={}",
            stats.artistsSeen,
            stats.artistsUpserted,
            stats.albumsSeen,
            stats.albumsUpserted,
            stats.tracksSeen,
            stats.tracksUpserted,
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

                val genreNames =
                    al.genres
                        ?.map { it.tag }
                        .orEmpty()

                albumGenreService.addGenres(album, genreNames)

                plexArtworkService.ensureAlbumCoverFromPlexThumb(
                    artist = artist,
                    album = album,
                    plexThumbPath = al.thumb,
                )

                importTracksForAlbum(
                    sectionKey = sectionKey,
                    albumRatingKey = al.ratingKey,
                    album = album,
                    stats = stats,
                    pageSize = pageSize,
                )

                stats.albumsUpserted++
            }

            start += albums.size
        }
    }

    private suspend fun importTracksForAlbum(
        sectionKey: String,
        albumRatingKey: String,
        album: Album,
        stats: ImportStats,
        pageSize: Int,
    ) {
        var start = 0
        var total = Int.MAX_VALUE

        while (start < total) {
            val (tracks, tot) = plexApi.listTracksByAlbumPaged(sectionKey, albumRatingKey, start, pageSize)
            total = tot
            if (tracks.isEmpty()) break

            for (track in tracks) {
                stats.tracksSeen++

                val mbidTrack = PlexGuidUtil.firstValue(track.guids, "mbid")
                val plexTrackGuid = PlexGuidUtil.firstValue(track.guids, "plex")?.let { "plex://$it" }

                canonical.ensureTrack(
                    album = album,
                    title = track.title,
                    trackNumber = track.index,
                    discNumber = track.parentIndex,
                    durationMs = track.duration?.toInt(),
                    musicbrainzId = mbidTrack,
                    plexGuid = plexTrackGuid,
                    spotifyId = null,
                )

                stats.tracksUpserted++
            }

            start += tracks.size
        }
    }
}
