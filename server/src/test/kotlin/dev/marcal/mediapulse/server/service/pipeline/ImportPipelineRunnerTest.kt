package dev.marcal.mediapulse.server.service.pipeline

import dev.marcal.mediapulse.server.config.HardcoverProperties
import dev.marcal.mediapulse.server.config.MusicBrainzProperties
import dev.marcal.mediapulse.server.config.PipelineProperties
import dev.marcal.mediapulse.server.config.PlexProperties
import dev.marcal.mediapulse.server.config.SpotifyProperties
import dev.marcal.mediapulse.server.service.hardcover.HardcoverImportService
import dev.marcal.mediapulse.server.service.musicbrainz.MusicBrainzAlbumGenreEnrichmentService
import dev.marcal.mediapulse.server.service.plex.import.PlexImportService
import dev.marcal.mediapulse.server.service.spotify.SpotifyImportService
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ImportPipelineRunnerTest {
    @MockK lateinit var pipelineProps: PipelineProperties

    @MockK lateinit var plexProps: PlexProperties

    @MockK lateinit var mbProps: MusicBrainzProperties

    @MockK lateinit var spotifyProps: SpotifyProperties

    @MockK lateinit var hardcoverProperties: HardcoverProperties

    @MockK lateinit var hardcoverImportService: HardcoverImportService

    @MockK lateinit var plexImportService: PlexImportService

    @MockK lateinit var mbService: MusicBrainzAlbumGenreEnrichmentService

    @MockK lateinit var spotifyImportService: SpotifyImportService

    private lateinit var runner: ImportPipelineRunner

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        runner =
            ImportPipelineRunner(
                pipelineProps = pipelineProps,
                plexProps = plexProps,
                mbProps = mbProps,
                spotifyProps = spotifyProps,
                plexImportService = plexImportService,
                mbService = mbService,
                spotifyImportService = spotifyImportService,
                hardcoverProperties = hardcoverProperties,
                hardcoverImportService = hardcoverImportService,
            )
    }

    @Test
    fun `should skip pipeline when disabled`() =
        runBlocking {
            coEvery { pipelineProps.enabled } returns false
            coEvery { hardcoverProperties.enabled } returns false
            runner.run("test")
            coVerify(exactly = 0) { plexImportService.importAllArtistsAndAlbums(any()) }
            coVerify(exactly = 0) { spotifyImportService.importRecentlyPlayed() }
            coVerify(exactly = 0) { mbService.enrichBatch(any()) }
            coVerify(exactly = 0) { hardcoverImportService.importUserBooks() }
        }

    @Test
    fun `should run all enabled services when pipeline is enabled`() =
        runBlocking {
            coEvery { pipelineProps.enabled } returns true
            coEvery { plexProps.import.enabled } returns true
            coEvery { plexProps.import.pageSize } returns 200
            coEvery { spotifyProps.enabled } returns true
            coEvery { spotifyProps.import.enabled } returns true
            coEvery { mbProps.enabled } returns true
            coEvery { mbProps.enrich.batchSize } returns 100
            coEvery { hardcoverProperties.enabled } returns true

            coEvery { plexImportService.importAllArtistsAndAlbums(pageSize = 200) } returns
                PlexImportService.ImportStats(
                    artistsSeen = 10,
                    artistsUpserted = 10,
                    albumsSeen = 50,
                    albumsUpserted = 50,
                    tracksSeen = 500,
                    tracksUpserted = 500,
                )
            coEvery { spotifyImportService.importRecentlyPlayed() } returns 100
            coEvery { mbService.enrichBatch(100) } returns 50
            coEvery { hardcoverImportService.importUserBooks() } returns 5

            runner.run("test")

            coVerify(exactly = 1) { plexImportService.importAllArtistsAndAlbums(pageSize = 200) }
            coVerify(exactly = 1) { spotifyImportService.importRecentlyPlayed() }
            coVerify(exactly = 1) { mbService.enrichBatch(100) }
            coVerify(exactly = 1) { hardcoverImportService.importUserBooks() }
        }

    @Test
    fun `should skip plex import when disabled`() =
        runBlocking {
            coEvery { pipelineProps.enabled } returns true
            coEvery { plexProps.import.enabled } returns false
            coEvery { spotifyProps.enabled } returns true
            coEvery { spotifyProps.import.enabled } returns true
            coEvery { mbProps.enabled } returns false
            coEvery { hardcoverProperties.enabled } returns false
            coEvery { spotifyImportService.importRecentlyPlayed() } returns 100

            runner.run("test")

            coVerify(exactly = 0) { plexImportService.importAllArtistsAndAlbums(any()) }
            coVerify(exactly = 1) { spotifyImportService.importRecentlyPlayed() }
            coVerify(exactly = 0) { mbService.enrichBatch(any()) }
            coVerify(exactly = 0) { hardcoverImportService.importUserBooks() }
        }
}
