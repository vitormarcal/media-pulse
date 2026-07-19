package dev.marcal.mediapulse.server.service.plex.import

import dev.marcal.mediapulse.server.integration.plex.PlexApiClient
import dev.marcal.mediapulse.server.integration.plex.dto.PlexEpisode
import dev.marcal.mediapulse.server.integration.plex.dto.PlexGuid
import dev.marcal.mediapulse.server.integration.plex.dto.PlexLibrarySection
import dev.marcal.mediapulse.server.integration.plex.dto.PlexShow
import dev.marcal.mediapulse.server.model.tv.TvEpisode
import dev.marcal.mediapulse.server.model.tv.TvShow
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
    private lateinit var plexShowArtworkService: PlexShowArtworkService
    private lateinit var service: PlexShowImportService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        plexApiClient = mockk()
        tvShowRepository = mockk(relaxed = true)
        tvShowTitleCrudRepository = mockk(relaxed = true)
        tvEpisodeRepository = mockk(relaxed = true)
        every { tvEpisodeRepository.findByTmdbId(any()) } returns null
        every { tvEpisodeRepository.findByTvdbId(any()) } returns null
        every { tvEpisodeRepository.findByImdbId(any()) } returns null
        plexShowArtworkService = mockk(relaxed = true)

        service =
            PlexShowImportService(
                plexApiClient = plexApiClient,
                tvShowRepository = tvShowRepository,
                tvShowTitleCrudRepository = tvShowTitleCrudRepository,
                tvEpisodeRepository = tvEpisodeRepository,
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
            every { tvShowRepository.findByTmdbId(any()) } returns null
            every { tvShowRepository.findByTvdbId(any()) } returns null
            every { tvShowRepository.findByImdbId(any()) } returns null
            every { tvShowRepository.findByFingerprint(any()) } returns null
            val savedShow =
                TvShow(
                    id = 10,
                    originalTitle = "Severance",
                    description = "show-desc",
                    year = 2022,
                    slug = "severance",
                    fingerprint = "show-fp",
                )
            every { tvShowRepository.findById(10) } answers { java.util.Optional.of(savedShow) }
            every { tvShowRepository.save(any()) } answers {
                val candidate = firstArg<TvShow>()
                if (candidate.id == 0L) candidate.copy(id = 10) else candidate
            }
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

            val stats = service.importAllShows(pageSize = 200)

            assertEquals(1, stats.showsSeen)
            assertEquals(1, stats.showsUpserted)
            assertEquals(1, stats.episodesSeen)
            assertEquals(1, stats.episodesUpserted)

            coVerify(exactly = 1) { plexApiClient.listShowSections() }
            coVerify(exactly = 1) { plexApiClient.listShowsPaged("2", 0, 200) }
            coVerify(exactly = 1) { plexApiClient.listEpisodesByShowPaged("2", "show-1", 0, 200) }
            verify(exactly = 1) { tvShowRepository.save(match { it.slug == "severance" && it.year == 2022 && it.id == 0L }) }
            verify(exactly = 1) { tvShowRepository.save(match { it.id == 10L && it.tmdbId == "95396" }) }
            verify(exactly = 1) { tvShowRepository.save(match { it.id == 10L && it.tvdbId == "371980" }) }
            coVerify(exactly = 1) {
                plexShowArtworkService.ensureShowImagesFromPlex(
                    match { it.id == 10L },
                    any(),
                    "/library/metadata/200/thumb/1",
                )
            }
            verify(exactly = 1) { tvEpisodeRepository.save(match { it.id == 0L && it.showId == 10L && it.episodeNumber == 1 }) }
            verify(exactly = 1) { tvEpisodeRepository.save(match { it.id == 20L && it.tvdbId != null && it.imdbId != null }) }
        }

    @Test
    fun `should merge existing show and episode using stable external ids without duplicating ext ids`() =
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

            val existingShow =
                TvShow(
                    id = 10,
                    originalTitle = "Severance",
                    description = null,
                    year = null,
                    slug = null,
                    tvdbId = "371980",
                    fingerprint = "show-fp",
                )
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

            every { tvShowRepository.findByTvdbId("371980") } returns existingShow
            every { tvEpisodeRepository.findByTvdbId("8956111") } returns existingEpisode.copy(tvdbId = "8956111")
            every { tvShowRepository.findById(10) } returns java.util.Optional.of(existingShow)
            every { tvEpisodeRepository.findById(20) } returns java.util.Optional.of(existingEpisode)
            every { tvShowRepository.findByFingerprint(any()) } returns null
            every { tvEpisodeRepository.findByFingerprint(any()) } returns existingEpisode
            every { tvShowRepository.save(any()) } returns existingShow.copy(description = "updated-show", year = 2022, slug = "severance")
            every { tvEpisodeRepository.save(any()) } answers { firstArg() }
            every { tvShowTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs

            val persistedShow = service.upsertShow(show)
            val persistedEpisode = service.upsertEpisode(persistedShow, episode)

            assertEquals("updated-show", persistedShow.description)
            assertEquals(2022, persistedShow.year)
            assertEquals("severance", persistedShow.slug)
            assertEquals("updated-episode", persistedEpisode.summary)
            assertEquals("8956111", persistedEpisode.tvdbId)

            verify(exactly = 1) {
                tvShowRepository.save(
                    match {
                        it.description == "updated-show" &&
                            it.slug == "severance" &&
                            it.year == 2022
                    },
                )
            }
            verify(exactly = 1) { tvEpisodeRepository.save(match { it.summary == "updated-episode" }) }
        }

    @Test
    fun `should reuse episode fingerprint after plex library rebuild changes plex ids`() {
        val show = TvShow(id = 10, originalTitle = "Severance", year = 2022, fingerprint = "show-fp")
        val existingEpisode =
            TvEpisode(
                id = 20,
                showId = 10,
                title = "Good News About Hell",
                seasonNumber = 1,
                episodeNumber = 1,
                fingerprint = "episode-fp",
            )
        val rebuiltPlexEpisode =
            PlexEpisode(
                ratingKey = "9876",
                title = "Good News About Hell",
                guid = "plex://episode/rebuilt-library-id",
                parentIndex = 1,
                index = 1,
                guids = emptyList(),
            )

        every { tvEpisodeRepository.findByFingerprint(any()) } returns existingEpisode

        val result = service.upsertEpisode(show, rebuiltPlexEpisode)

        assertEquals(20, result.id)
        verify(exactly = 0) { tvEpisodeRepository.save(any()) }
    }

    @Test
    fun `should preserve current episode id when plex sends a conflicting provider id`() {
        val show = TvShow(id = 10, originalTitle = "Severance", year = 2022, fingerprint = "show-fp")
        val existingEpisode =
            TvEpisode(
                id = 20,
                showId = 10,
                title = "Good News About Hell",
                seasonNumber = 1,
                episodeNumber = 1,
                fingerprint = "episode-fp",
                tvdbId = "8956111",
            )
        val plexEpisode =
            PlexEpisode(
                ratingKey = "ep-1",
                title = "Good News About Hell",
                guid = "plex://episode/ep-1",
                parentIndex = 1,
                index = 1,
                guids = listOf(PlexGuid("tvdb://9999999")),
            )

        every { tvEpisodeRepository.findByFingerprint(any()) } returns existingEpisode

        val result = service.upsertEpisode(show, plexEpisode)

        assertEquals("8956111", result.tvdbId)
        verify(exactly = 0) { tvEpisodeRepository.save(any()) }
    }

    @Test
    fun `should ignore ambiguous ids from the same provider`() {
        val show = TvShow(id = 10, originalTitle = "Severance", year = 2022, fingerprint = "show-fp")
        val existingEpisode =
            TvEpisode(
                id = 20,
                showId = 10,
                title = "Good News About Hell",
                seasonNumber = 1,
                episodeNumber = 1,
                fingerprint = "episode-fp",
            )
        val plexEpisode =
            PlexEpisode(
                ratingKey = "ep-1",
                title = "Good News About Hell",
                guid = "plex://episode/ep-1",
                parentIndex = 1,
                index = 1,
                guids = listOf(PlexGuid("tvdb://111"), PlexGuid("tvdb://222")),
            )

        every { tvEpisodeRepository.findByFingerprint(any()) } returns existingEpisode

        val result = service.upsertEpisode(show, plexEpisode)

        assertEquals(20, result.id)
        assertEquals(null, result.tvdbId)
        verify(exactly = 0) { tvEpisodeRepository.findByTvdbId("111") }
        verify(exactly = 0) { tvEpisodeRepository.findByTvdbId("222") }
        verify(exactly = 0) { tvEpisodeRepository.save(any()) }
    }
}
