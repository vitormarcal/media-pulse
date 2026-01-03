package dev.marcal.mediapulse.server.service.spotify

import dev.marcal.mediapulse.server.integration.spotify.SpotifyApiClient
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.repository.query.SpotifyBackfillQueryRepository
import dev.marcal.mediapulse.server.service.canonical.CanonicalizationService
import dev.marcal.mediapulse.server.util.TxUtil
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
class SpotifyAlbumTracklistBackfillService(
    private val queryRepo: SpotifyBackfillQueryRepository,
    private val spotifyApi: SpotifyApiClient,
    private val canonical: CanonicalizationService,
    private val albumRepo: AlbumRepository,
    private val artistRepo: ArtistRepository,
    private val tx: TxUtil,
    private val em: EntityManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)

    data class BackfillResult(
        val albumsSeen: Int,
        val albumsBackfilled: Int,
        val linksUpserted: Int,
        val tracksSeenFromSpotify: Int,
        val errors: Int,
    )

    /**
     * Backfills disc/track positions for albums that:
     * - have some album_tracks with NULL position
     * - have a Spotify album id in external_identifiers
     *
     * Robust version:
     * - Always uses the canonical album artist (albums.artist_id) as the Track artist.
     * - Ignores artists from Spotify tracklist to avoid creating tracks under "wrong" artists
     *   for compilations/feats/odd metadata.
     */
    suspend fun backfillTop(limit: Int = 50): BackfillResult {
        if (!running.compareAndSet(false, true)) {
            logger.info("Spotify album tracklist backfill already running | ignored")
            return BackfillResult(0, 0, 0, 0, 0)
        }

        try {
            val targets = queryRepo.findAlbumsToBackfill(limit)
            var albumsBackfilled = 0
            var linksUpserted = 0
            var tracksSeen = 0
            var errors = 0

            for (t in targets) {
                try {
                    // Fetch tracklist from Spotify outside TX (network).
                    val items = spotifyApi.getAllAlbumTracks(t.spotifyAlbumId)
                    tracksSeen += items.size

                    tx.inTx {
                        val album: Album =
                            albumRepo.findById(t.albumId).orElseThrow {
                                IllegalStateException("Album not found: ${t.albumId}")
                            }

                        val albumArtist: Artist =
                            artistRepo.findById(album.artistId).orElseThrow {
                                IllegalStateException("Artist not found for albumId=${album.id} artistId=${album.artistId}")
                            }

                        var i = 0
                        for (it in items) {
                            val trackSpotifyId = it.id ?: continue
                            val trackTitle = it.name ?: continue
                            val trackNumber = it.trackNumber ?: continue
                            val discNumber = it.discNumber ?: 1
                            val durationMs = it.durationMs

                            // Always use album's canonical artist.
                            val trackEntity =
                                canonical.ensureTrack(
                                    artist = albumArtist,
                                    title = trackTitle,
                                    durationMs = durationMs,
                                    spotifyId = trackSpotifyId,
                                )

                            try {
                                canonical.linkTrackToAlbum(
                                    album = album,
                                    track = trackEntity,
                                    discNumber = discNumber,
                                    trackNumber = trackNumber,
                                )
                                linksUpserted++
                            } catch (e: Exception) {
                                logger.warn(
                                    "Backfill link failed | albumId={} trackId={} disc={} track={} title={}",
                                    album.id,
                                    trackEntity.id,
                                    discNumber,
                                    trackNumber,
                                    trackTitle,
                                    e,
                                )
                            }

                            linksUpserted++

                            i++
                            if (i % 200 == 0) {
                                em.flush()
                                em.clear()
                            }
                        }

                        em.flush()
                        em.clear()
                    }

                    albumsBackfilled++
                    logger.info(
                        "Backfilled album tracks | albumId={} spotifyAlbumId={} tracksFromSpotify={} prevWithoutPos={} prevWithPos={}",
                        t.albumId,
                        t.spotifyAlbumId,
                        items.size,
                        t.withoutPosition,
                        t.withPosition,
                    )
                } catch (e: Exception) {
                    errors++
                    logger.error(
                        "Backfill failed for album | albumId={} spotifyAlbumId={}",
                        t.albumId,
                        t.spotifyAlbumId,
                        e,
                    )
                }
            }

            return BackfillResult(
                albumsSeen = targets.size,
                albumsBackfilled = albumsBackfilled,
                linksUpserted = linksUpserted,
                tracksSeenFromSpotify = tracksSeen,
                errors = errors,
            )
        } finally {
            running.set(false)
        }
    }
}
