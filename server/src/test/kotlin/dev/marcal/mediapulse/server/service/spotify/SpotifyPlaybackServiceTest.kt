package dev.marcal.mediapulse.server.service.spotify

import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyAlbum
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyArtist
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyImage
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyRecentlyPlayedItem
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyTrack
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.model.music.PlaybackSource
import dev.marcal.mediapulse.server.model.music.Track
import dev.marcal.mediapulse.server.repository.crud.TrackPlaybackCrudRepository
import dev.marcal.mediapulse.server.service.canonical.CanonicalizationService
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class SpotifyPlaybackServiceTest {
    @MockK lateinit var canonical: CanonicalizationService

    @MockK lateinit var trackPlaybackRepo: TrackPlaybackCrudRepository

    @MockK lateinit var spotifyArtworkService: SpotifyArtworkService

    private lateinit var service: SpotifyPlaybackService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service =
            SpotifyPlaybackService(
                canonical = canonical,
                trackPlaybackRepo = trackPlaybackRepo,
                spotifyArtworkService = spotifyArtworkService,
            )
    }

    @Test
    fun `should return early when item track is null`() =
        runBlocking {
            val item =
                SpotifyRecentlyPlayedItem(
                    playedAt = "2020-01-01T00:00:01Z",
                    track = null,
                )

            service.processRecentlyPlayedItem(item = item, eventId = 123L)

            verify(exactly = 0) { canonical.ensureArtist(any(), any(), any()) }
            verify(exactly = 0) { canonical.ensureAlbum(any(), any(), any(), any(), any(), any()) }
            verify(exactly = 0) { canonical.ensureTrack(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { canonical.linkTrackToAlbum(any(), any(), any(), any()) }
            verify(exactly = 0) { trackPlaybackRepo.insertIgnore(any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { spotifyArtworkService.ensureAlbumCoverFromSpotifyUrl(any(), any(), any()) }
        }

    @Test
    fun `should return early when track name is null`() =
        runBlocking {
            val item =
                SpotifyRecentlyPlayedItem(
                    playedAt = "2020-01-01T00:00:01Z",
                    track =
                        SpotifyTrack(
                            id = "t1",
                            name = null,
                            durationMs = 100,
                            trackNumber = 1,
                            discNumber = 1,
                            artists = listOf(SpotifyArtist(id = "a1", name = "Artist")),
                            album =
                                SpotifyAlbum(
                                    id = "al1",
                                    name = "Album",
                                    releaseDate = "2020-01-01",
                                    images = emptyList(),
                                    artists = null,
                                ),
                        ),
                )

            service.processRecentlyPlayedItem(item = item, eventId = 123L)

            verify(exactly = 0) { canonical.ensureArtist(any(), any(), any()) }
            verify(exactly = 0) { canonical.ensureAlbum(any(), any(), any(), any(), any(), any()) }
            verify(exactly = 0) { canonical.ensureTrack(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { canonical.linkTrackToAlbum(any(), any(), any(), any()) }
            verify(exactly = 0) { trackPlaybackRepo.insertIgnore(any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { spotifyArtworkService.ensureAlbumCoverFromSpotifyUrl(any(), any(), any()) }
        }

    @Test
    fun `should process item end-to-end using track artists and biggest cover image`() =
        runBlocking {
            val playedAtStr = "2020-01-01T00:00:01Z"
            val playedAt = Instant.parse(playedAtStr)

            val item =
                SpotifyRecentlyPlayedItem(
                    playedAt = playedAtStr,
                    track =
                        SpotifyTrack(
                            id = "t_spotify",
                            name = "Track Name",
                            durationMs = 123_000,
                            trackNumber = 7,
                            discNumber = 2,
                            artists = listOf(SpotifyArtist(id = "a_spotify", name = "Main Artist")),
                            album =
                                SpotifyAlbum(
                                    id = "al_spotify",
                                    name = "Album Name",
                                    releaseDate = "1999-12-31",
                                    images =
                                        listOf(
                                            SpotifyImage(url = "small", width = 64, height = 64),
                                            SpotifyImage(url = "biggest", width = 640, height = 640),
                                            SpotifyImage(url = "unknownWidth", width = null, height = null),
                                        ),
                                    artists = listOf(SpotifyArtist(id = "albumArtist", name = "Album Artist")),
                                ),
                        ),
                )

            val artistEntity = Artist(id = 10L, name = "Main Artist", fingerprint = "fpA")
            val albumEntity =
                Album(
                    id = 20L,
                    artistId = 10L,
                    title = "Album Name",
                    titleKey = "album-name",
                    year = 1999,
                    coverUrl = null,
                    fingerprint = "fpB",
                )
            val trackEntity = Track(id = 30L, artistId = 10L, title = "Track Name", durationMs = 123_000, fingerprint = "fpT")

            every {
                canonical.ensureArtist(
                    name = "Main Artist",
                    spotifyId = "a_spotify",
                    musicbrainzId = null,
                )
            } returns artistEntity

            every {
                canonical.ensureAlbum(
                    artist = artistEntity,
                    title = "Album Name",
                    year = 1999,
                    coverUrl = null,
                    spotifyId = "al_spotify",
                )
            } returns albumEntity

            every {
                canonical.ensureTrack(
                    artist = artistEntity,
                    title = "Track Name",
                    durationMs = 123_000,
                    spotifyId = "t_spotify",
                )
            } returns trackEntity

            every {
                canonical.linkTrackToAlbum(
                    album = albumEntity,
                    track = trackEntity,
                    discNumber = 2,
                    trackNumber = 7,
                )
            } just runs

            every {
                trackPlaybackRepo.insertIgnore(
                    trackId = 30L,
                    albumId = 20L,
                    source = PlaybackSource.SPOTIFY.name,
                    sourceEventId = 999L,
                    playedAt = playedAt,
                )
            } just runs

            coEvery {
                spotifyArtworkService.ensureAlbumCoverFromSpotifyUrl(
                    artist = artistEntity,
                    album = albumEntity,
                    spotifyImageUrl = "biggest",
                )
            } just runs

            service.processRecentlyPlayedItem(item = item, eventId = 999L)

            verify(exactly = 1) {
                canonical.ensureArtist(
                    name = "Main Artist",
                    spotifyId = "a_spotify",
                    musicbrainzId = null,
                )
            }
            verify(exactly = 1) {
                canonical.ensureAlbum(
                    artist = artistEntity,
                    title = "Album Name",
                    year = 1999,
                    coverUrl = null,
                    spotifyId = "al_spotify",
                )
            }
            verify(exactly = 1) {
                canonical.ensureTrack(
                    artist = artistEntity,
                    title = "Track Name",
                    durationMs = 123_000,
                    spotifyId = "t_spotify",
                )
            }
            verify(exactly = 1) {
                canonical.linkTrackToAlbum(
                    album = albumEntity,
                    track = trackEntity,
                    discNumber = 2,
                    trackNumber = 7,
                )
            }
            verify(exactly = 1) {
                trackPlaybackRepo.insertIgnore(
                    trackId = 30L,
                    albumId = 20L,
                    source = PlaybackSource.SPOTIFY.name,
                    sourceEventId = 999L,
                    playedAt = playedAt,
                )
            }
            coVerify(exactly = 1) {
                spotifyArtworkService.ensureAlbumCoverFromSpotifyUrl(
                    artist = artistEntity,
                    album = albumEntity,
                    spotifyImageUrl = "biggest",
                )
            }
        }

    @Test
    fun `should fallback to Unknown artist and Unknown album when missing data and still insert playback`() =
        runBlocking {
            val playedAtStr = "2020-01-01T00:00:01Z"
            val playedAt = Instant.parse(playedAtStr)

            val item =
                SpotifyRecentlyPlayedItem(
                    playedAt = playedAtStr,
                    track =
                        SpotifyTrack(
                            id = "t1",
                            name = "Track",
                            durationMs = null,
                            trackNumber = null,
                            discNumber = null,
                            artists = null, // sem artista
                            album = null, // sem album
                        ),
                )

            val artistEntity = Artist(id = 1L, name = "Unknown", fingerprint = "fpU")
            val albumEntity =
                Album(
                    id = 2L,
                    artistId = 1L,
                    title = "Unknown",
                    titleKey = "unknown",
                    year = null,
                    coverUrl = null,
                    fingerprint = "fpA",
                )
            val trackEntity = Track(id = 3L, artistId = 1L, title = "Track", durationMs = null, fingerprint = "fpT")

            every { canonical.ensureArtist(name = "Unknown", spotifyId = null, musicbrainzId = null) } returns artistEntity
            every {
                canonical.ensureAlbum(
                    artist = artistEntity,
                    title = "Unknown",
                    year = null,
                    coverUrl = null,
                    spotifyId = null,
                )
            } returns
                albumEntity
            every { canonical.ensureTrack(artist = artistEntity, title = "Track", durationMs = null, spotifyId = "t1") } returns trackEntity

            every { canonical.linkTrackToAlbum(album = albumEntity, track = trackEntity, discNumber = null, trackNumber = null) } just runs

            every {
                trackPlaybackRepo.insertIgnore(
                    trackId = 3L,
                    albumId = 2L,
                    source = PlaybackSource.SPOTIFY.name,
                    sourceEventId = null,
                    playedAt = playedAt,
                )
            } just runs

            // coverUrl nulo -> serviço de artwork recebe null e pode retornar; aqui só stub pra não ser "no answer"
            coEvery {
                spotifyArtworkService.ensureAlbumCoverFromSpotifyUrl(
                    artist = artistEntity,
                    album = albumEntity,
                    spotifyImageUrl = null,
                )
            } just
                runs

            service.processRecentlyPlayedItem(item = item, eventId = null)

            verify(exactly = 1) { canonical.ensureArtist("Unknown", null, null) }
            verify(exactly = 1) { canonical.ensureAlbum(artistEntity, "Unknown", null, null, null, null) }
            verify(exactly = 1) { canonical.ensureTrack(artistEntity, "Track", null, null, "t1") }

            verify(exactly = 1) { canonical.linkTrackToAlbum(albumEntity, trackEntity, null, null) }
            verify(exactly = 1) { trackPlaybackRepo.insertIgnore(3L, 2L, PlaybackSource.SPOTIFY.name, null, playedAt) }
            coVerify(exactly = 1) { spotifyArtworkService.ensureAlbumCoverFromSpotifyUrl(artistEntity, albumEntity, null) }
        }
}
