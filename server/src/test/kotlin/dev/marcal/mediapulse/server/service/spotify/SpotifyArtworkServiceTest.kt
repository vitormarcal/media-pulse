package dev.marcal.mediapulse.server.service.spotify

import dev.marcal.mediapulse.server.integration.spotify.SpotifyImageClient
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.service.canonical.CanonicalizationService
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

class SpotifyArtworkServiceTest {
    @MockK lateinit var spotifyImageClient: SpotifyImageClient

    @MockK lateinit var imageStorageService: ImageStorageService

    @MockK lateinit var canonical: CanonicalizationService

    private lateinit var service: SpotifyArtworkService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service =
            SpotifyArtworkService(
                spotifyImageClient = spotifyImageClient,
                imageStorageService = imageStorageService,
                canonical = canonical,
            )
    }

    @Test
    fun `should return early when spotifyImageUrl is null or blank`() =
        runBlocking {
            val artist = Artist(id = 1L, name = "A", fingerprint = "fpA")
            val album =
                Album(
                    id = 2L,
                    artistId = 1L,
                    title = "B",
                    titleKey = "b",
                    year = 2020,
                    coverUrl = null,
                    fingerprint = "fpB",
                )

            service.ensureAlbumCoverFromSpotifyUrl(artist, album, null)
            service.ensureAlbumCoverFromSpotifyUrl(artist, album, "")
            service.ensureAlbumCoverFromSpotifyUrl(artist, album, "   ")

            coVerify(exactly = 0) { spotifyImageClient.downloadImage(any()) }
            coVerify(exactly = 0) { imageStorageService.saveImageForAlbum(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { canonical.updateAlbumCoverIfEmpty(any(), any()) }

            confirmVerified(spotifyImageClient, imageStorageService, canonical)
        }

    @Test
    fun `should return early when album already has coverUrl`() =
        runBlocking {
            val artist = Artist(id = 1L, name = "A", fingerprint = "fpA")
            val album =
                Album(
                    id = 2L,
                    artistId = 1L,
                    title = "B",
                    titleKey = "b",
                    year = 2020,
                    coverUrl = "/already/has/cover.jpg",
                    fingerprint = "fpB",
                )

            service.ensureAlbumCoverFromSpotifyUrl(artist, album, "https://img")

            coVerify(exactly = 0) { spotifyImageClient.downloadImage(any()) }
            coVerify(exactly = 0) { imageStorageService.saveImageForAlbum(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { canonical.updateAlbumCoverIfEmpty(any(), any()) }

            confirmVerified(spotifyImageClient, imageStorageService, canonical)
        }

    @Test
    fun `should download store and update cover when url is present and album has no cover`() =
        runBlocking {
            val artist = Artist(id = 10L, name = "Metallica", fingerprint = "fpA")
            val album =
                Album(
                    id = 20L,
                    artistId = 10L,
                    title = "Ride the Lightning",
                    titleKey = "ride-the-lightning",
                    year = 1984,
                    coverUrl = null,
                    fingerprint = "fpB",
                )

            val url = "https://image"
            val image = ImageContent(bytes = byteArrayOf(1, 2, 3), contentType = MediaType.IMAGE_JPEG)
            val localPath = "/covers/spotify/10/20_metallica_ride_the_lightning.jpg"

            coEvery { spotifyImageClient.downloadImage(url) } returns image

            coEvery {
                imageStorageService.saveImageForAlbum(
                    image = image,
                    provider = "SPOTIFY",
                    artistId = 10L,
                    albumId = 20L,
                    fileNameHint = "Metallica_Ride the Lightning",
                )
            } returns localPath

            every { canonical.updateAlbumCoverIfEmpty(20L, localPath) } returns album

            service.ensureAlbumCoverFromSpotifyUrl(artist, album, url)

            coVerify(exactly = 1) { spotifyImageClient.downloadImage(url) }
            coVerify(exactly = 1) {
                imageStorageService.saveImageForAlbum(
                    image = image,
                    provider = "SPOTIFY",
                    artistId = 10L,
                    albumId = 20L,
                    fileNameHint = "Metallica_Ride the Lightning",
                )
            }
            verify(exactly = 1) { canonical.updateAlbumCoverIfEmpty(20L, localPath) }

            confirmVerified(spotifyImageClient, imageStorageService, canonical)
        }

    @Test
    fun `should swallow exceptions and not call update cover when download fails`() =
        runBlocking {
            val artist = Artist(id = 1L, name = "A", fingerprint = "fpA")
            val album =
                Album(
                    id = 2L,
                    artistId = 1L,
                    title = "B",
                    titleKey = "b",
                    year = 2020,
                    coverUrl = null,
                    fingerprint = "fpB",
                )

            val url = "https://image"

            coEvery { spotifyImageClient.downloadImage(url) } throws RuntimeException("boom")

            service.ensureAlbumCoverFromSpotifyUrl(artist, album, url)

            coVerify(exactly = 1) { spotifyImageClient.downloadImage(url) }
            coVerify(exactly = 0) { imageStorageService.saveImageForAlbum(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { canonical.updateAlbumCoverIfEmpty(any(), any()) }
        }

    @Test
    fun `should swallow exceptions and not call update cover when storage fails`() =
        runBlocking {
            val artist = Artist(id = 1L, name = "A", fingerprint = "fpA")
            val album =
                Album(
                    id = 2L,
                    artistId = 1L,
                    title = "B",
                    titleKey = "b",
                    year = 2020,
                    coverUrl = null,
                    fingerprint = "fpB",
                )

            val url = "https://image"
            val image = ImageContent(bytes = byteArrayOf(9), contentType = MediaType.IMAGE_PNG)

            coEvery { spotifyImageClient.downloadImage(url) } returns image

            coEvery {
                imageStorageService.saveImageForAlbum(
                    image = image,
                    provider = "SPOTIFY",
                    artistId = 1L,
                    albumId = 2L,
                    fileNameHint = "A_B",
                )
            } throws RuntimeException("disk full")

            service.ensureAlbumCoverFromSpotifyUrl(artist, album, url)

            coVerify(exactly = 1) { spotifyImageClient.downloadImage(url) }
            coVerify(exactly = 1) {
                imageStorageService.saveImageForAlbum(
                    image = image,
                    provider = "SPOTIFY",
                    artistId = 1L,
                    albumId = 2L,
                    fileNameHint = "A_B",
                )
            }
            verify(exactly = 0) { canonical.updateAlbumCoverIfEmpty(any(), any()) }
        }
}
