package dev.marcal.mediapulse.server.controller.shows

import dev.marcal.mediapulse.server.api.shows.CurrentlyWatchingShowDto
import dev.marcal.mediapulse.server.api.shows.RangeDto
import dev.marcal.mediapulse.server.api.shows.ShowCreditsBatchSyncResponse
import dev.marcal.mediapulse.server.api.shows.ShowCreditsSyncResponse
import dev.marcal.mediapulse.server.api.shows.ShowDetailsResponse
import dev.marcal.mediapulse.server.api.shows.ShowProgressDto
import dev.marcal.mediapulse.server.api.shows.ShowSeasonDetailsResponse
import dev.marcal.mediapulse.server.api.shows.ShowSeasonEpisodeDto
import dev.marcal.mediapulse.server.api.shows.ShowYearUnwatchedDto
import dev.marcal.mediapulse.server.api.shows.ShowYearWatchedDto
import dev.marcal.mediapulse.server.api.shows.ShowsByYearResponse
import dev.marcal.mediapulse.server.api.shows.ShowsByYearStatsDto
import dev.marcal.mediapulse.server.api.shows.ShowsRecentResponse
import dev.marcal.mediapulse.server.api.shows.ShowsStatsResponse
import dev.marcal.mediapulse.server.api.shows.ShowsSummaryResponse
import dev.marcal.mediapulse.server.api.shows.ShowsTotalStatsDto
import dev.marcal.mediapulse.server.api.shows.ShowsYearStatsDto
import dev.marcal.mediapulse.server.repository.TvShowQueryRepository
import dev.marcal.mediapulse.server.service.tv.ShowCreditsService
import dev.marcal.mediapulse.server.service.tv.ShowSeasonMetadataEnrichmentService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShowsControllerTest {
    private val repository = mockk<TvShowQueryRepository>(relaxed = true)
    private val showSeasonMetadataEnrichmentService = mockk<ShowSeasonMetadataEnrichmentService>(relaxed = true)
    private val showCreditsService = mockk<ShowCreditsService>(relaxed = true)
    private val controller = ShowsController(repository, showSeasonMetadataEnrichmentService, showCreditsService)

    @Test
    fun `recent should delegate to repository`() {
        every { repository.recent(15, null) } returns ShowsRecentResponse(items = emptyList(), nextCursor = null)

        val result = controller.recent(15, null)

        assertEquals(0, result.items.size)
        verify(exactly = 1) { repository.recent(15, null) }
    }

    @Test
    fun `currently watching should delegate with computed cutoff`() {
        val expected =
            listOf(
                CurrentlyWatchingShowDto(
                    showId = 29,
                    title = "O Cavaleiro dos Sete Reinos",
                    originalTitle = "A Knight of the Seven Kingdoms",
                    slug = "a-knight-of-the-seven-kingdoms",
                    year = 2026,
                    coverUrl = "/covers/plex/tv-shows/29/poster.jpg",
                    lastWatchedAt = Instant.parse("2026-04-03T23:02:53Z"),
                    progress =
                        ShowProgressDto(
                            episodesCount = 6,
                            watchedEpisodesCount = 4,
                            seasonsCount = 1,
                            completedSeasonsCount = 0,
                            completed = false,
                            inProgress = true,
                        ),
                ),
            )
        every {
            repository.currentlyWatching(20, match { cutoff -> Duration.between(cutoff, Instant.now()).toDays() in 89..91 })
        } returns expected

        val response = controller.currentlyWatching(limit = 20, activeWithinDays = 90)

        assertEquals(1, response.size)
        assertEquals(29L, response.first().showId)
        verify(exactly = 1) {
            repository.currentlyWatching(20, match { cutoff -> Duration.between(cutoff, Instant.now()).toDays() in 89..91 })
        }
    }

    @Test
    fun `currently watching should reject invalid params`() {
        assertFailsWith<ResponseStatusException> {
            controller.currentlyWatching(limit = 0, activeWithinDays = 90)
        }
        assertFailsWith<ResponseStatusException> {
            controller.currentlyWatching(limit = 20, activeWithinDays = 0)
        }
    }

    @Test
    fun `details by slug should delegate to repository`() {
        val expected =
            ShowDetailsResponse(
                showId = 10,
                title = "Ruptura",
                originalTitle = "Severance",
                year = 2022,
                description = null,
                coverUrl = null,
                images = emptyList(),
                progress =
                    ShowProgressDto(
                        episodesCount = 0,
                        watchedEpisodesCount = 0,
                        seasonsCount = 0,
                        completedSeasonsCount = 0,
                        completed = false,
                        inProgress = false,
                    ),
                watches = emptyList(),
                externalIds = emptyList(),
            )
        every { repository.getShowDetailsBySlug("severance") } returns expected

        val response = controller.detailsBySlug("severance")

        assertEquals(10, response.showId)
        verify(exactly = 1) { repository.getShowDetailsBySlug("severance") }
    }

    @Test
    fun `season details by slug should delegate to repository`() {
        val expected =
            ShowSeasonDetailsResponse(
                showId = 10,
                showSlug = "the-big-bang-theory",
                showTitle = "The Big Bang Theory",
                showOriginalTitle = "The Big Bang Theory",
                showYear = 2007,
                showCoverUrl = "/covers/show.jpg",
                showTmdbId = "1418",
                seasonNumber = 1,
                seasonTitle = "Temporada 1",
                episodesCount = 17,
                watchedEpisodesCount = 3,
                completed = false,
                lastWatchedAt = Instant.parse("2024-01-03T14:35:00Z"),
                episodes =
                    listOf(
                        ShowSeasonEpisodeDto(
                            episodeId = 99,
                            title = "Pilot",
                            episodeNumber = 1,
                            summary = null,
                            durationMs = 1200000,
                            originallyAvailableAt = null,
                            watchCount = 1,
                            lastWatchedAt = Instant.parse("2024-01-03T14:35:00Z"),
                        ),
                    ),
            )
        every { repository.getShowSeasonDetailsBySlug("the-big-bang-theory", 1) } returns expected

        val response = controller.seasonDetailsBySlug("the-big-bang-theory", 1)

        assertEquals(10, response.showId)
        assertEquals(1, response.seasonNumber)
        verify(exactly = 1) { repository.getShowSeasonDetailsBySlug("the-big-bang-theory", 1) }
    }

    @Test
    fun `sync credits should delegate to show credits service`() {
        every { showCreditsService.syncFromTmdb(10) } returns
            ShowCreditsSyncResponse(showId = 10, syncedCount = 8, visibleCount = 8)

        val response = controller.syncCreditsFromTmdb(10)

        assertEquals(8, response.syncedCount)
        verify(exactly = 1) { showCreditsService.syncFromTmdb(10) }
    }

    @Test
    fun `batch sync credits should delegate to show credits service with capped limit`() {
        every { showCreditsService.syncAllFromTmdb(1000) } returns
            ShowCreditsBatchSyncResponse(
                requestedLimit = 1000,
                candidates = 15,
                processed = 15,
                synced = 14,
                failed = 1,
            )

        val response = controller.syncAllCreditsFromTmdb(5000)

        assertEquals(14, response.synced)
        verify(exactly = 1) { showCreditsService.syncAllFromTmdb(1000) }
    }

    @Test
    fun `summary custom without dates should fail`() {
        assertFailsWith<IllegalArgumentException> {
            controller.summary(range = "custom", start = null, end = null)
        }
    }

    @Test
    fun `summary custom should delegate with provided range`() {
        val start = Instant.parse("2026-02-01T00:00:00Z")
        val end = Instant.parse("2026-02-26T00:00:00Z")
        val expected = ShowsSummaryResponse(RangeDto(start, end), watchesCount = 5, uniqueShowsCount = 2)

        every { repository.summary(start, end) } returns expected

        val response = controller.summary(range = "custom", start = start, end = end)

        assertEquals(5, response.watchesCount)
        assertEquals(2, response.uniqueShowsCount)
        verify(exactly = 1) { repository.summary(start, end) }
    }

    @Test
    fun `by year should delegate with computed range and default limits`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val end = Instant.parse("2026-12-31T23:59:59Z")
        val expected =
            ShowsByYearResponse(
                year = 2026,
                range = RangeDto(start, end),
                stats = ShowsByYearStatsDto(watchesCount = 2, uniqueShowsCount = 1, rewatchesCount = 1),
                watched =
                    listOf(
                        ShowYearWatchedDto(
                            showId = 1,
                            slug = "severance",
                            title = "Ruptura",
                            originalTitle = "Severance",
                            year = 2022,
                            coverUrl = "/covers/plex/tv-shows/1/poster.jpg",
                            watchCountInYear = 2,
                            firstWatchedAt = Instant.parse("2026-01-10T21:00:00Z"),
                            lastWatchedAt = Instant.parse("2026-02-27T19:40:19Z"),
                        ),
                    ),
                unwatched =
                    listOf(
                        ShowYearUnwatchedDto(
                            showId = 9,
                            slug = "dark",
                            title = "Dark",
                            originalTitle = "Dark",
                            year = 2017,
                            coverUrl = "/covers/plex/tv-shows/9/poster.jpg",
                        ),
                    ),
            )
        every { repository.byYear(2026, start, end, 200, 200) } returns expected

        val response = controller.byYear(year = 2026, limitWatched = 200, limitUnwatched = 200)

        assertEquals(2026, response.year)
        assertEquals(1, response.watched.size)
        verify(exactly = 1) { repository.byYear(2026, start, end, 200, 200) }
    }

    @Test
    fun `by year should cap limits to 1000`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val end = Instant.parse("2026-12-31T23:59:59Z")
        every {
            repository.byYear(2026, start, end, 1000, 1000)
        } returns
            ShowsByYearResponse(
                year = 2026,
                range = RangeDto(start, end),
                stats = ShowsByYearStatsDto(watchesCount = 0, uniqueShowsCount = 0, rewatchesCount = 0),
                watched = emptyList(),
                unwatched = emptyList(),
            )

        controller.byYear(year = 2026, limitWatched = 5000, limitUnwatched = 9999)

        verify(exactly = 1) { repository.byYear(2026, start, end, 1000, 1000) }
    }

    @Test
    fun `by year should reject invalid year`() {
        assertFailsWith<ResponseStatusException> {
            controller.byYear(year = 1899, limitWatched = 200, limitUnwatched = 200)
        }
    }

    @Test
    fun `by year should reject invalid limits`() {
        assertFailsWith<ResponseStatusException> {
            controller.byYear(year = 2026, limitWatched = 0, limitUnwatched = 200)
        }
        assertFailsWith<ResponseStatusException> {
            controller.byYear(year = 2026, limitWatched = 200, limitUnwatched = 0)
        }
    }

    @Test
    fun `stats should delegate to repository`() {
        val expected =
            ShowsStatsResponse(
                total = ShowsTotalStatsDto(watchesCount = 120, uniqueShowsCount = 85),
                unwatchedCount = 240,
                years =
                    listOf(
                        ShowsYearStatsDto(year = 2026, watchesCount = 42, uniqueShowsCount = 30, rewatchesCount = 12),
                        ShowsYearStatsDto(year = 2025, watchesCount = 18, uniqueShowsCount = 16, rewatchesCount = 2),
                    ),
                latestWatchAt = Instant.parse("2026-02-27T19:40:19Z"),
                firstWatchAt = Instant.parse("2024-01-10T11:00:00Z"),
            )
        every { repository.stats() } returns expected

        val response = controller.stats()

        assertEquals(240, response.unwatchedCount)
        assertEquals(2, response.years.size)
        verify(exactly = 1) { repository.stats() }
    }
}
