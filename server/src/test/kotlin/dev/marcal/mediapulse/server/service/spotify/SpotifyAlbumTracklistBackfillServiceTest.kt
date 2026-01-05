package dev.marcal.mediapulse.server.service.spotify

import dev.marcal.mediapulse.server.integration.spotify.SpotifyApiClient
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyAlbumTrackItem
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.model.music.Track
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.repository.query.SpotifyBackfillQueryRepository
import dev.marcal.mediapulse.server.service.canonical.CanonicalizationService
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.Optional
import kotlin.test.Test

class SpotifyAlbumTracklistBackfillServiceTest {
    private val queryRepo = mockk<SpotifyBackfillQueryRepository>()
    private val spotifyApi = mockk<SpotifyApiClient>()
    private val canonical = mockk<CanonicalizationService>()
    private val albumRepo = mockk<AlbumRepository>()
    private val artistRepo = mockk<ArtistRepository>()
    private val tx = mockk<TxUtil>()
    private val em = mockk<EntityManager>(relaxed = true)

    private val service =
        SpotifyAlbumTracklistBackfillService(
            queryRepo = queryRepo,
            spotifyApi = spotifyApi,
            canonical = canonical,
            albumRepo = albumRepo,
            artistRepo = artistRepo,
            tx = tx,
            em = em,
        )

    @Test
    fun `should backfill one album, skip invalid items, and count result`(): Unit =
        runBlocking {
            val target =
                SpotifyBackfillQueryRepository.AlbumToBackfill(
                    albumId = 10L,
                    spotifyAlbumId = "sp_alb_1",
                    withoutPosition = 5,
                    withPosition = 1,
                )

            every { queryRepo.findAlbumsToBackfill(50) } returns listOf(target)
            every { queryRepo.findAlbumsToBackfill(50) } returns listOf(target)

            // Ajuste aqui se o construtor real do SpotifyAlbumTrackItem for diferente no seu projeto.
            val items =
                listOf(
                    SpotifyAlbumTrackItem(
                        id = "t1",
                        name = "Track 1",
                        trackNumber = 1,
                        discNumber = 1,
                        durationMs = 111,
                    ),
                    SpotifyAlbumTrackItem(
                        id = null, // skip
                        name = "Track X",
                        trackNumber = 2,
                        discNumber = 1,
                        durationMs = 222,
                    ),
                    SpotifyAlbumTrackItem(
                        id = "t2",
                        name = "Track 2",
                        trackNumber = 2,
                        discNumber = null, // default 1
                        durationMs = null,
                    ),
                )

            coEvery { spotifyApi.getAllAlbumTracks("sp_alb_1", any()) } returns items

            // TxUtil executa bloco inline (sem transação real no unit test)
            every { tx.inTx<Any>(any()) } answers { firstArg<() -> Any>().invoke() }

            val album =
                Album(
                    id = 10L,
                    artistId = 99L,
                    title = "Alb",
                    titleKey = "alb",
                    year = 2020,
                    coverUrl = null,
                    fingerprint = "fp",
                )
            every { albumRepo.findById(10L) } returns Optional.of(album)

            val artist =
                Artist(
                    id = 99L,
                    name = "Artist",
                    fingerprint = "afp",
                )
            every { artistRepo.findById(99L) } returns Optional.of(artist)

            val track1 = Track(id = 1001L, artistId = 99L, title = "Track 1", durationMs = 111, fingerprint = "x1")
            val track2 = Track(id = 1002L, artistId = 99L, title = "Track 2", durationMs = null, fingerprint = "x2")

            every {
                canonical.ensureTrack(
                    artist = artist,
                    title = "Track 1",
                    durationMs = 111,
                    spotifyId = "t1",
                    musicbrainzId = null,
                )
            } returns track1

            every {
                canonical.ensureTrack(
                    artist = artist,
                    title = "Track 2",
                    durationMs = null,
                    spotifyId = "t2",
                    musicbrainzId = null,
                )
            } returns track2

            every {
                canonical.linkTrackToAlbum(
                    album = album,
                    track = track1,
                    discNumber = 1,
                    trackNumber = 1,
                )
            } just Runs

            every {
                canonical.linkTrackToAlbum(
                    album = album,
                    track = track2,
                    discNumber = 1, // default
                    trackNumber = 2,
                )
            } just Runs

            val result = service.backfillTop(limit = 50)

            assertEquals(1, result.albumsSeen)
            assertEquals(1, result.albumsBackfilled)
            assertEquals(3, result.tracksSeenFromSpotify) // conta todos que vieram da API
            assertEquals(0, result.errors)

            // links upserted:
            // Atenção: no seu service tem um `linksUpserted++` DUPLICADO (um dentro do try e outro fora).
            // Se você já corrigiu, o esperado é 2. Se não corrigiu ainda, vai dar 4.
            assertEquals(2, result.linksUpserted)

            verify(exactly = 1) { queryRepo.findAlbumsToBackfill(50) }
            coVerify(exactly = 1) { spotifyApi.getAllAlbumTracks("sp_alb_1", any()) }

            verify(exactly = 1) { albumRepo.findById(10L) }
            verify(exactly = 1) { artistRepo.findById(99L) }

            verify(exactly = 2) { canonical.ensureTrack(any(), any(), any(), any(), any()) }
            verify(exactly = 2) { canonical.linkTrackToAlbum(any(), any(), any(), any()) }

            verify(atLeast = 1) { em.flush() }
            verify(atLeast = 1) { em.clear() }
        }

    @Test
    fun `should count error when album not found`() =
        runBlocking {
            val target =
                SpotifyBackfillQueryRepository.AlbumToBackfill(
                    albumId = 10L,
                    spotifyAlbumId = "sp_alb_1",
                    withoutPosition = 5,
                    withPosition = 1,
                )

            every { queryRepo.findAlbumsToBackfill(50) } returns listOf(target)

            coEvery { spotifyApi.getAllAlbumTracks("sp_alb_1", any()) } returns emptyList()

            every { tx.inTx<Any>(any()) } answers { firstArg<() -> Any>().invoke() }

            every { albumRepo.findById(10L) } returns Optional.empty()

            val result = service.backfillTop(limit = 50)

            assertEquals(1, result.albumsSeen)
            assertEquals(0, result.albumsBackfilled)
            assertEquals(0, result.tracksSeenFromSpotify)
            assertEquals(1, result.errors)

            verify(exactly = 0) { canonical.ensureTrack(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { canonical.linkTrackToAlbum(any(), any(), any(), any()) }
        }

    @Test
    fun `should ignore second call while first is running`(): Unit =
        runBlocking {
            every { queryRepo.findAlbumsToBackfill(50) } returns
                listOf(
                    SpotifyBackfillQueryRepository.AlbumToBackfill(
                        albumId = 1L,
                        spotifyAlbumId = "sp",
                        withoutPosition = 1,
                        withPosition = 0,
                    ),
                )

            // primeira execução vai suspender aqui (delay), segurando running=true
            coEvery { spotifyApi.getAllAlbumTracks(any(), any()) } coAnswers {
                delay(200)
                emptyList()
            }

            every { tx.inTx<Any>(any()) } answers { firstArg<() -> Any>().invoke() }
            every { albumRepo.findById(1L) } returns
                Optional.of(
                    Album(
                        id = 1L,
                        artistId = 1L,
                        title = "A",
                        titleKey = "a",
                        year = null,
                        coverUrl = null,
                        fingerprint = "fp",
                    ),
                )
            every { artistRepo.findById(1L) } returns
                Optional.of(
                    Artist(id = 1L, name = "X", fingerprint = "xfp"),
                )

            val first =
                async(start = CoroutineStart.UNDISPATCHED) {
                    service.backfillTop(50)
                }

            val ignored = service.backfillTop(50)
            assertEquals(
                SpotifyAlbumTracklistBackfillService.BackfillResult(0, 0, 0, 0, 0),
                ignored,
            )

            first.await()
        }
}
