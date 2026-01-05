package dev.marcal.mediapulse.server.service.pipeline

import dev.marcal.mediapulse.server.config.MusicBrainzProperties
import dev.marcal.mediapulse.server.config.PipelineProperties
import dev.marcal.mediapulse.server.config.PlexProperties
import dev.marcal.mediapulse.server.config.SpotifyProperties
import dev.marcal.mediapulse.server.service.musicbrainz.MusicBrainzAlbumGenreEnrichmentService
import dev.marcal.mediapulse.server.service.plex.import.PlexImportService
import dev.marcal.mediapulse.server.service.spotify.SpotifyImportService
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class ImportPipelineRunnerTest {
    private val pipelineProps = mockk<PipelineProperties>()
    private val plexProps = mockk<PlexProperties>()
    private val mbProps = mockk<MusicBrainzProperties>()
    private val spotifyProps = mockk<SpotifyProperties>()

    private val plexImportService = mockk<PlexImportService>()
    private val mbService = mockk<MusicBrainzAlbumGenreEnrichmentService>()
    private val spotifyImportService = mockk<SpotifyImportService>()

    private val runner =
        ImportPipelineRunner(
            pipelineProps = pipelineProps,
            plexProps = plexProps,
            mbProps = mbProps,
            spotifyProps = spotifyProps,
            plexImportService = plexImportService,
            mbService = mbService,
            spotifyImportService = spotifyImportService,
        )

    @Test
    fun `should do nothing when pipeline is disabled`() =
        runBlocking {
            every { pipelineProps.enabled } returns false

            runner.run("x")

            verify { plexImportService wasNot Called }
            verify { spotifyImportService wasNot Called }
            verify { mbService wasNot Called }
        }

    @Test
    fun `should run enabled steps and skip disabled ones`() =
        runBlocking {
            every { pipelineProps.enabled } returns true

            // Plex enabled
            every { plexProps.import.enabled } returns true
            every { plexProps.import.pageSize } returns 123
            val stats = mockk<PlexImportService.ImportStats>()
            every { stats.artistsSeen } returns 1
            every { stats.albumsSeen } returns 2
            every { stats.tracksSeen } returns 3
            coEvery { plexImportService.importAllArtistsAndAlbums(pageSize = 123) } returns stats

            // Spotify disabled
            every { spotifyProps.enabled } returns true
            every { spotifyProps.import.enabled } returns false

            // MB enabled
            every { mbProps.enabled } returns true
            every { mbProps.enrich.batchSize } returns 77
            coEvery { mbService.enrichBatch(77) } returns 10

            runner.run("manual")

            coVerify(exactly = 1) { plexImportService.importAllArtistsAndAlbums(pageSize = 123) }
            coVerify(exactly = 0) { spotifyImportService.importRecentlyPlayed() }
            coVerify(exactly = 1) { mbService.enrichBatch(77) }
        }

    @Test
    fun `should ignore second call while already running`() =
        runBlocking {
            every { pipelineProps.enabled } returns true

            every { plexProps.import.enabled } returns true
            every { plexProps.import.pageSize } returns 1

            val stats = mockk<PlexImportService.ImportStats>(relaxed = true)

            // IMPORTANT: stub com a assinatura REAL (2 params)
            coEvery { plexImportService.importAllArtistsAndAlbums(any(), any()) } coAnswers {
                delay(200)
                stats
            }

            every { spotifyProps.enabled } returns false
            every { spotifyProps.import.enabled } returns false
            every { mbProps.enabled } returns false

            val first =
                async(start = CoroutineStart.UNDISPATCHED) {
                    runner.run("first")
                }

            // Agora running=true com certeza
            runner.run("second")

            first.await()

            coVerify(exactly = 1) { plexImportService.importAllArtistsAndAlbums(any(), any()) }
        }
}
