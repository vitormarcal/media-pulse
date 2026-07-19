package dev.marcal.mediapulse.server.service.musicbrainz

import dev.marcal.mediapulse.server.config.MusicBrainzProperties
import dev.marcal.mediapulse.server.integration.musicbrainz.MusicBrainzApiClient
import dev.marcal.mediapulse.server.integration.musicbrainz.MusicBrainzClientException
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.AlbumMusicBrainzReleaseId
import dev.marcal.mediapulse.server.model.music.GenreSource
import dev.marcal.mediapulse.server.repository.crud.AlbumGenreSyncStateRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumMusicBrainzReleaseIdRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.repository.query.AlbumQueryRepository
import dev.marcal.mediapulse.server.service.music.AlbumGenreService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals

class MusicBrainzAlbumGenreEnrichmentServiceTest {
    private val props = mockk<MusicBrainzProperties>()
    private val albumQuery = mockk<AlbumQueryRepository>()
    private val albumRepo = mockk<AlbumRepository>()
    private val releaseIds = mockk<AlbumMusicBrainzReleaseIdRepository>()
    private val syncRepo = mockk<AlbumGenreSyncStateRepository>()
    private val client = mockk<MusicBrainzApiClient>()
    private val genres = mockk<AlbumGenreService>()
    private val service =
        MusicBrainzAlbumGenreEnrichmentService(props, albumQuery, albumRepo, releaseIds, syncRepo, client, genres)

    private fun common(album: Album) {
        every { props.enabled } returns true
        every { props.enrich } returns MusicBrainzProperties.Enrich(batchSize = 10, maxTags = 10, minRequestIntervalMs = 1)
        every { albumQuery.findAlbumIdsPending(10, GenreSource.MUSICBRAINZ.name) } returns listOf(album.id)
        every { syncRepo.shouldFetch(album.id, GenreSource.MUSICBRAINZ.name) } returns true
        every { albumRepo.findById(album.id) } returns Optional.of(album)
        every { syncRepo.markDone(album.id, GenreSource.MUSICBRAINZ.name, null) } just runs
    }

    @Test
    fun `returns zero when integration is disabled`() =
        runBlocking {
            every { props.enabled } returns false

            assertEquals(0, service.enrichBatch(10))
        }

    @Test
    fun `skips album already processed`() =
        runBlocking {
            every { props.enabled } returns true
            every { props.enrich } returns MusicBrainzProperties.Enrich(batchSize = 10, maxTags = 10, minRequestIntervalMs = 1)
            every { albumQuery.findAlbumIdsPending(10, GenreSource.MUSICBRAINZ.name) } returns listOf(1)
            every { syncRepo.shouldFetch(1, GenreSource.MUSICBRAINZ.name) } returns false

            assertEquals(0, service.enrichBatch(10))
            coVerify(exactly = 0) { client.getAlbumGenreNamesByMbid(any(), any()) }
            coVerify(exactly = 0) { client.getReleaseGroupGenreNamesByMbid(any(), any()) }
        }

    @Test
    fun `marks album done when no musicbrainz identifier exists`() =
        runBlocking {
            val album = Album(id = 1, artistId = 2, title = "Album", titleKey = "album", fingerprint = "fp")
            every { props.enabled } returns true
            every { props.enrich } returns MusicBrainzProperties.Enrich(batchSize = 10, maxTags = 10, minRequestIntervalMs = 1)
            every { albumQuery.findAlbumIdsPending(10, GenreSource.MUSICBRAINZ.name) } returns listOf(1)
            every { syncRepo.shouldFetch(1, GenreSource.MUSICBRAINZ.name) } returns true
            every { albumRepo.findById(1) } returns Optional.of(album)
            every { releaseIds.findFirstByAlbumIdOrderByIdAsc(1) } returns null
            every { syncRepo.markDone(1, GenreSource.MUSICBRAINZ.name, "NO_MBID") } just runs

            assertEquals(1, service.enrichBatch(10))
        }

    @Test
    fun `marks empty release group result as done`() =
        runBlocking {
            val album =
                Album(
                    id = 1,
                    artistId = 2,
                    title = "Album",
                    titleKey = "album",
                    fingerprint = "fp",
                    musicbrainzReleaseGroupId = "rg-1",
                )
            common(album)
            every { releaseIds.findFirstByAlbumIdOrderByIdAsc(1) } returns null
            coEvery { client.getReleaseGroupGenreNamesByMbid("rg-1", 10) } returns emptyList()
            every { syncRepo.markDone(1, GenreSource.MUSICBRAINZ.name, "EMPTY_RESULT") } just runs

            assertEquals(1, service.enrichBatch(10))
            coVerify(exactly = 0) { genres.addGenres(any(), any(), any()) }
        }

    @Test
    fun `marks retryable release group failure for retry`() =
        runBlocking {
            val album =
                Album(
                    id = 1,
                    artistId = 2,
                    title = "Album",
                    titleKey = "album",
                    fingerprint = "fp",
                    musicbrainzReleaseGroupId = "rg-1",
                )
            common(album)
            every { releaseIds.findFirstByAlbumIdOrderByIdAsc(1) } returns null
            coEvery { client.getReleaseGroupGenreNamesByMbid("rg-1", 10) } throws
                MusicBrainzClientException.Retryable("boom", RuntimeException("x"), "rg-1", "/ws/2/release-group/rg-1")
            every { syncRepo.markFailed(1, GenreSource.MUSICBRAINZ.name, any(), true) } just runs

            assertEquals(1, service.enrichBatch(10))
            io.mockk.verify { syncRepo.markFailed(1, GenreSource.MUSICBRAINZ.name, match { it.startsWith("MB_RETRYABLE") }, true) }
        }

    @Test
    fun `uses canonical release group when available`() =
        runBlocking {
            val album =
                Album(
                    id = 1,
                    artistId = 2,
                    title = "Album",
                    titleKey = "album",
                    fingerprint = "fp",
                    musicbrainzReleaseGroupId = "rg-1",
                )
            common(album)
            every { releaseIds.findFirstByAlbumIdOrderByIdAsc(1) } returns null
            coEvery { client.getReleaseGroupGenreNamesByMbid("rg-1", 10) } returns listOf("rock")
            coEvery { genres.addGenres(album, listOf("rock"), GenreSource.MUSICBRAINZ) } just runs

            assertEquals(1, service.enrichBatch(10))
            coVerify { client.getReleaseGroupGenreNamesByMbid("rg-1", 10) }
        }

    @Test
    fun `falls back to technical release alias`() =
        runBlocking {
            val album = Album(id = 1, artistId = 2, title = "Album", titleKey = "album", fingerprint = "fp")
            common(album)
            every { releaseIds.findFirstByAlbumIdOrderByIdAsc(1) } returns AlbumMusicBrainzReleaseId(albumId = 1, releaseId = "release-1")
            coEvery { client.getAlbumGenreNamesByMbid("release-1", 10) } returns listOf("rock")
            coEvery { genres.addGenres(album, listOf("rock"), GenreSource.MUSICBRAINZ) } just runs

            assertEquals(1, service.enrichBatch(10))
            coVerify { client.getAlbumGenreNamesByMbid("release-1", 10) }
        }
}
