package dev.marcal.mediapulse.server.service.plex.import

import dev.marcal.mediapulse.server.integration.plex.PlexApiClient
import dev.marcal.mediapulse.server.integration.plex.dto.PlexEpisode
import dev.marcal.mediapulse.server.integration.plex.dto.PlexGuid
import dev.marcal.mediapulse.server.integration.plex.dto.PlexLibrarySection
import dev.marcal.mediapulse.server.integration.plex.dto.PlexShow
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.tv.TvEpisode
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowTitleCrudRepository
import dev.marcal.mediapulse.server.service.plex.PlexShowArtworkService
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class PlexShowImportServiceTest {
    private lateinit var plexApiClient: PlexApiClient
    private lateinit var tvShowRepository: TvShowRepository
    private lateinit var tvShowTitleCrudRepository: TvShowTitleCrudRepository
    private lateinit var tvEpisodeRepository: TvEpisodeRepository
    private lateinit var externalIdentifierRepository: ExternalIdentifierRepository
    private lateinit var plexShowArtworkService: PlexShowArtworkService
    private lateinit var service: PlexShowImportService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        plexApiClient = mockk()
        tvShowRepository = mockk(relaxed = true)
        tvShowTitleCrudRepository = mockk(relaxed = true)
        tvEpisodeRepository = mockk(relaxed = true)
        externalIdentifierRepository = mockk(relaxed = true)
        plexShowArtworkService = mockk(relaxed = true)

        service =
            PlexShowImportService(
                plexApiClient = plexApiClient,
                tvShowRepository = tvShowRepository,
                tvShowTitleCrudRepository = tvShowTitleCrudRepository,
                tvEpisodeRepository = tvEpisodeRepository,
                externalIdentifierRepository = externalIdentifierRepository,
                plexShowArtworkService = plexShowArtworkService,
            )
    }

    @Test
    fun `should import shows and episodes from plex sections`() =
        runBlocking {
            val section = PlexLibrarySection(key = "2", type = "show")
            val show =
                PlexShow(
                    ratingKey = "show-1",
                    slug = "severance",
                    title = "Ruptura",
                    originalTitle = "Severance",
                    year = 2022,
                    summary = "show-desc",
                    guid = "plex://show/abc123",
                    thumb = "/library/metadata/200/thumb/1",
                    guids = listOf(PlexGuid("tvdb://371980"), PlexGuid("tmdb://95396")),
                )
            val episode =
                PlexEpisode(
                    ratingKey = "ep-1",
                    title = "Good News About Hell",
                    guid = "plex://episode/ep1",
                    parentIndex = 1,
                    index = 1,
                    summary = "ep-desc",
                    duration = 3420000,
                    originallyAvailableAt = LocalDate.parse("2022-02-18"),
                    guids = listOf(PlexGuid("tvdb://8956111"), PlexGuid("imdb://tt11280740")),
                )

            coEvery { plexApiClient.listShowSections() } returns listOf(section)
            coEvery { plexApiClient.listShowsPaged("2", 0, 200) } returns (listOf(show) to 1)
            coEvery { plexApiClient.listEpisodesByShowPaged("2", "show-1", 0, 200) } returns (listOf(episode) to 1)

            every {
                externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(any(), any(), any())
            } returns null
            every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns null
            every { tvShowRepository.findByFingerprint(any()) } returns null
            every { tvShowRepository.findByShowTitle(any()) } returns null
            every { tvShowRepository.findById(any()) } returns java.util.Optional.empty()
            every {
                tvShowRepository.save(match { it.originalTitle == "Severance" && it.slug == "severance" })
            } returns TvShow(id = 10, originalTitle = "Severance", description = "show-desc", slug = "severance", fingerprint = "show-fp")
            every { tvShowTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
            coEvery { plexShowArtworkService.ensureShowImagesFromPlex(any(), any(), any()) } returns Unit

            every { tvEpisodeRepository.findByFingerprint(any()) } returns null
            every { tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(10, 1, 1) } returns null
            every { tvEpisodeRepository.findById(any()) } returns java.util.Optional.empty()
            every {
                tvEpisodeRepository.save(
                    match {
                        it.showId == 10L &&
                            it.title == "Good News About Hell" &&
                            it.seasonNumber == 1 &&
                            it.episodeNumber == 1
                    },
                )
            } returns
                TvEpisode(
                    id = 20,
                    showId = 10,
                    title = "Good News About Hell",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    summary = "ep-desc",
                    durationMs = 3420000,
                    originallyAvailableAt = LocalDate.parse("2022-02-18"),
                    fingerprint = "ep-fp",
                )
            every { externalIdentifierRepository.save(any()) } returns mockk()

            val stats = service.importAllShows(pageSize = 200)

            assertEquals(1, stats.showsSeen)
            assertEquals(1, stats.showsUpserted)
            assertEquals(1, stats.episodesSeen)
            assertEquals(1, stats.episodesUpserted)

            coVerify(exactly = 1) { plexApiClient.listShowSections() }
            coVerify(exactly = 1) { plexApiClient.listShowsPaged("2", 0, 200) }
            coVerify(exactly = 1) { plexApiClient.listEpisodesByShowPaged("2", "show-1", 0, 200) }
            verify(exactly = 1) { tvShowRepository.save(match { it.slug == "severance" }) }
            coVerify(exactly = 1) {
                plexShowArtworkService.ensureShowImagesFromPlex(
                    match { it.id == 10L },
                    any(),
                    "/library/metadata/200/thumb/1",
                )
            }
            verify(exactly = 1) { tvEpisodeRepository.save(match { it.showId == 10L && it.episodeNumber == 1 }) }
            verify(exactly = 6) {
                externalIdentifierRepository.save(
                    match {
                        (
                            it.entityType == EntityType.SHOW &&
                                (it.provider == Provider.PLEX || it.provider == Provider.TVDB || it.provider == Provider.TMDB)
                        ) ||
                            (
                                it.entityType == EntityType.EPISODE &&
                                    (it.provider == Provider.PLEX || it.provider == Provider.TVDB || it.provider == Provider.IMDB)
                            )
                    },
                )
            }
        }

    @Test
    fun `should merge existing show and episode without duplicating ext ids`() =
        runBlocking {
            val show =
                PlexShow(
                    ratingKey = "show-1",
                    slug = "severance",
                    title = "Ruptura",
                    originalTitle = "Severance",
                    year = 2022,
                    summary = "updated-show",
                    guid = "plex://show/abc123",
                    guids = listOf(PlexGuid("tvdb://371980")),
                )
            val episode =
                PlexEpisode(
                    ratingKey = "ep-1",
                    title = "Good News About Hell",
                    guid = "plex://episode/ep1",
                    parentIndex = 1,
                    index = 1,
                    summary = "updated-episode",
                    guids = listOf(PlexGuid("tvdb://8956111")),
                )

            val existingShow = TvShow(id = 10, originalTitle = "Severance", description = null, slug = null, fingerprint = "show-fp")
            val existingEpisode =
                TvEpisode(
                    id = 20,
                    showId = 10,
                    title = "Good News About Hell",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    summary = null,
                    durationMs = null,
                    fingerprint = "ep-fp",
                )

            every {
                externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(EntityType.SHOW, Provider.PLEX, "plex://show/abc123")
            } returns null
            every {
                externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(
                    EntityType.EPISODE,
                    Provider.PLEX,
                    "plex://episode/ep1",
                )
            } returns null
            every { externalIdentifierRepository.findByProviderAndExternalId(Provider.PLEX, "plex://show/abc123") } returns mockk()
            every { externalIdentifierRepository.findByProviderAndExternalId(Provider.TVDB, "371980") } returns mockk()
            every { externalIdentifierRepository.findByProviderAndExternalId(Provider.PLEX, "plex://episode/ep1") } returns mockk()
            every { externalIdentifierRepository.findByProviderAndExternalId(Provider.TVDB, "8956111") } returns mockk()
            every { tvShowRepository.findByFingerprint(any()) } returns existingShow
            every { tvEpisodeRepository.findByFingerprint(any()) } returns existingEpisode
            every { tvShowRepository.save(any()) } returns existingShow.copy(description = "updated-show", slug = "severance")
            every { tvEpisodeRepository.save(any()) } returns existingEpisode.copy(summary = "updated-episode")
            every { tvShowTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs

            val persistedShow = service.upsertShow(show)
            val persistedEpisode = service.upsertEpisode(persistedShow, episode)

            assertEquals("updated-show", persistedShow.description)
            assertEquals("severance", persistedShow.slug)
            assertEquals("updated-episode", persistedEpisode.summary)

            verify(exactly = 1) { tvShowRepository.save(match { it.description == "updated-show" && it.slug == "severance" }) }
            verify(exactly = 1) { tvEpisodeRepository.save(match { it.summary == "updated-episode" }) }
            verify(exactly = 0) { externalIdentifierRepository.save(any()) }
        }
}
