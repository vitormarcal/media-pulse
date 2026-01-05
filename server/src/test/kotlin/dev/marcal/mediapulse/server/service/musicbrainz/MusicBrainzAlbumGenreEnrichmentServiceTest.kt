package dev.marcal.mediapulse.server.service.musicbrainz

import dev.marcal.mediapulse.server.config.MusicBrainzProperties
import dev.marcal.mediapulse.server.integration.musicbrainz.MusicBrainzApiClient
import dev.marcal.mediapulse.server.integration.musicbrainz.MusicBrainzClientException
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.GenreSource
import dev.marcal.mediapulse.server.repository.crud.AlbumGenreSyncStateRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.query.AlbumQueryRepository
import dev.marcal.mediapulse.server.service.music.AlbumGenreService
import io.mockk.Called
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class MusicBrainzAlbumGenreEnrichmentServiceTest {
    private val props = mockk<MusicBrainzProperties>()
    private val albumQuery = mockk<AlbumQueryRepository>()
    private val albumRepo = mockk<AlbumRepository>()
    private val extIds = mockk<ExternalIdentifierRepository>()
    private val syncRepo = mockk<AlbumGenreSyncStateRepository>()
    private val mbClient = mockk<MusicBrainzApiClient>()
    private val albumGenreService = mockk<AlbumGenreService>()

    private lateinit var service: MusicBrainzAlbumGenreEnrichmentService

    @BeforeEach
    fun setup() {
        clearAllMocks()

        every { props.enabled } returns true
        every { props.enrich } returns MusicBrainzProperties.Enrich(batchSize = 200, maxTags = 10, minRequestIntervalMs = 1)

        service =
            MusicBrainzAlbumGenreEnrichmentService(
                props = props,
                albumQuery = albumQuery,
                albumRepo = albumRepo,
                extIds = extIds,
                syncRepo = syncRepo,
                musicBrainzApiClient = mbClient,
                albumGenreService = albumGenreService,
            )
    }

    @Test
    fun `should return 0 when disabled`() =
        runBlocking {
            every { props.enabled } returns false

            val processed = service.enrichBatch(limit = 10)

            assert(processed == 0)
            verify { albumQuery wasNot Called }
            verify { syncRepo wasNot Called }
            coVerify { mbClient wasNot Called }
            coVerify { albumGenreService wasNot Called }
        }

    @Test
    fun `should return 0 when batch has no candidates`() =
        runBlocking {
            every { albumQuery.findAlbumIdsPending(limit = 10, source = GenreSource.MUSICBRAINZ.name) } returns emptyList()

            val processed = service.enrichBatch(limit = 10)

            assert(processed == 0)
            verify(exactly = 1) { albumQuery.findAlbumIdsPending(10, GenreSource.MUSICBRAINZ.name) }
            verify { syncRepo wasNot Called }
            coVerify { mbClient wasNot Called }
            coVerify { albumGenreService wasNot Called }
        }

    @Test
    fun `should skip album when shouldFetch is false`() =
        runBlocking {
            val albumId = 123L
            every { albumQuery.findAlbumIdsPending(limit = 10, source = GenreSource.MUSICBRAINZ.name) } returns listOf(albumId)
            every { syncRepo.shouldFetch(albumId, GenreSource.MUSICBRAINZ.name) } returns false

            val processed = service.enrichBatch(limit = 10)

            // retorna "processado" como skippedDone não entra no retorno final (enriched/empty/noMbid/missing/failed)
            // então aqui deve ser 0.
            assert(processed == 0)

            verify(exactly = 1) { syncRepo.shouldFetch(albumId, GenreSource.MUSICBRAINZ.name) }
            verify { extIds wasNot Called }
            verify { albumRepo wasNot Called }
            coVerify { mbClient wasNot Called }
            coVerify { albumGenreService wasNot Called }
            verify(exactly = 0) { syncRepo.markDone(any(), any(), any()) }
            verify(exactly = 0) { syncRepo.markFailed(any(), any(), any(), any()) }
        }

    @Test
    fun `should mark done NO_MBID when external identifier missing`() =
        runBlocking {
            val albumId = 10L
            every { albumQuery.findAlbumIdsPending(limit = 10, source = GenreSource.MUSICBRAINZ.name) } returns listOf(albumId)
            every { syncRepo.shouldFetch(albumId, GenreSource.MUSICBRAINZ.name) } returns true

            every {
                extIds.findFirstByEntityTypeAndProviderAndEntityId(
                    entityType = EntityType.ALBUM,
                    entityId = albumId,
                    provider = Provider.MUSICBRAINZ,
                )
            } returns null

            every { syncRepo.markDone(albumId, GenreSource.MUSICBRAINZ.name, "NO_MBID") } just Runs

            val processed = service.enrichBatch(limit = 10)

            assert(processed == 1)

            verify(exactly = 1) { syncRepo.markDone(albumId, GenreSource.MUSICBRAINZ.name, "NO_MBID") }
            coVerify { mbClient wasNot Called }
            coVerify { albumGenreService wasNot Called }
            verify { albumRepo wasNot Called }
        }

    @Test
    fun `should mark done EMPTY_RESULT when tags is empty`() =
        runBlocking {
            val albumId = 11L
            val mbid = "mbid-11"

            every { albumQuery.findAlbumIdsPending(limit = 10, source = GenreSource.MUSICBRAINZ.name) } returns listOf(albumId)
            every { syncRepo.shouldFetch(albumId, GenreSource.MUSICBRAINZ.name) } returns true

            every {
                extIds.findFirstByEntityTypeAndProviderAndEntityId(
                    entityType = EntityType.ALBUM,
                    entityId = albumId,
                    provider = Provider.MUSICBRAINZ,
                )
            } returns
                ExternalIdentifier(
                    id = 1,
                    provider = Provider.MUSICBRAINZ,
                    entityType = EntityType.ALBUM,
                    entityId = albumId,
                    externalId = mbid,
                )

            coEvery { mbClient.getAlbumGenreNamesByMbid(mbid, max = 10) } returns emptyList()

            val album =
                Album(
                    id = albumId,
                    artistId = 1,
                    title = "Album 11",
                    titleKey = "album-11",
                    year = null,
                    coverUrl = null,
                    fingerprint = "fp-11",
                )
            every { albumRepo.findById(albumId) } returns Optional.of(album)

            every { syncRepo.markDone(albumId, GenreSource.MUSICBRAINZ.name, "EMPTY_RESULT") } just Runs

            val processed = service.enrichBatch(limit = 10)

            assert(processed == 1)
            verify(exactly = 1) { syncRepo.markDone(albumId, GenreSource.MUSICBRAINZ.name, "EMPTY_RESULT") }
            coVerify(exactly = 0) { albumGenreService.addGenres(any(), any(), any()) }
        }

    @Test
    fun `should enrich and mark done when tags present`() =
        runBlocking {
            val albumId = 12L
            val mbid = "mbid-12"
            val tags = listOf("death metal", "grindcore")

            every { albumQuery.findAlbumIdsPending(limit = 10, source = GenreSource.MUSICBRAINZ.name) } returns listOf(albumId)
            every { syncRepo.shouldFetch(albumId, GenreSource.MUSICBRAINZ.name) } returns true

            every {
                extIds.findFirstByEntityTypeAndProviderAndEntityId(
                    entityType = EntityType.ALBUM,
                    entityId = albumId,
                    provider = Provider.MUSICBRAINZ,
                )
            } returns
                ExternalIdentifier(
                    id = 1,
                    provider = Provider.MUSICBRAINZ,
                    entityType = EntityType.ALBUM,
                    entityId = albumId,
                    externalId = mbid,
                )

            coEvery { mbClient.getAlbumGenreNamesByMbid(mbid, max = 10) } returns tags

            val album =
                Album(
                    id = albumId,
                    artistId = 1,
                    title = "Album 12",
                    titleKey = "album-12",
                    year = 2020,
                    coverUrl = null,
                    fingerprint = "fp-12",
                )
            every { albumRepo.findById(albumId) } returns Optional.of(album)

            coEvery { albumGenreService.addGenres(album, tags, GenreSource.MUSICBRAINZ) } just Runs
            every { syncRepo.markDone(albumId, GenreSource.MUSICBRAINZ.name, null) } just Runs

            val processed = service.enrichBatch(limit = 10)

            assert(processed == 1)
            coVerify(exactly = 1) { albumGenreService.addGenres(album, tags, GenreSource.MUSICBRAINZ) }
            verify(exactly = 1) { syncRepo.markDone(albumId, GenreSource.MUSICBRAINZ.name, null) }
        }

    @Test
    fun `should mark failed with forceNext when retryable`() =
        runBlocking {
            val albumId = 13L
            val mbid = "mbid-13"

            every { albumQuery.findAlbumIdsPending(limit = 10, source = GenreSource.MUSICBRAINZ.name) } returns listOf(albumId)
            every { syncRepo.shouldFetch(albumId, GenreSource.MUSICBRAINZ.name) } returns true

            every {
                extIds.findFirstByEntityTypeAndProviderAndEntityId(
                    entityType = EntityType.ALBUM,
                    entityId = albumId,
                    provider = Provider.MUSICBRAINZ,
                )
            } returns
                ExternalIdentifier(
                    id = 1,
                    provider = Provider.MUSICBRAINZ,
                    entityType = EntityType.ALBUM,
                    entityId = albumId,
                    externalId = mbid,
                )

            coEvery { mbClient.getAlbumGenreNamesByMbid(mbid, max = 10) } throws
                MusicBrainzClientException.Retryable("boom", RuntimeException("x"), mbid, "/ws/2/release/$mbid")

            every {
                syncRepo.markFailed(
                    albumId = albumId,
                    source = GenreSource.MUSICBRAINZ.name,
                    error = match { it.startsWith("MB_RETRYABLE endpoint=") },
                    forceNext = true,
                )
            } just Runs

            val processed = service.enrichBatch(limit = 10)

            assert(processed == 1)
            verify(exactly = 1) {
                syncRepo.markFailed(
                    albumId = albumId,
                    source = GenreSource.MUSICBRAINZ.name,
                    error = any(),
                    forceNext = true,
                )
            }
        }
}
