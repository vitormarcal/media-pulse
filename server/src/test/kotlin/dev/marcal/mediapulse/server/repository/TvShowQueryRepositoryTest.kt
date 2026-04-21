package dev.marcal.mediapulse.server.repository

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TvShowQueryRepositoryTest {
    private val em = mockk<EntityManager>()
    private val query = mockk<Query>(relaxed = true)
    private lateinit var repository: TvShowQueryRepository
    private val sqls = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        repository = TvShowQueryRepository(em)
        sqls.clear()

        every { em.createNativeQuery(any<String>()) } answers {
            sqls += firstArg<String>()
            query
        }
        every { query.setParameter(any<String>(), any()) } returns query
        every { query.resultList } returns emptyList<Any>()
        every { query.singleResult } returns 0L
    }

    @Test
    fun `recent should map rows`() {
        every { query.resultList } returns
            listOf(
                arrayOf(
                    10L,
                    "Ruptura",
                    "Severance",
                    "severance",
                    2022,
                    "/covers/plex/tv-shows/10/poster.jpg",
                    Timestamp.from(Instant.parse("2026-02-26T10:00:00Z")),
                ),
            )

        val result = repository.recent(limit = 5, cursor = null)

        assertEquals(1, result.items.size)
        assertEquals(10L, result.items[0].showId)
        assertEquals("Ruptura", result.items[0].title)
        assertEquals("Severance", result.items[0].originalTitle)
        assertEquals("severance", result.items[0].slug)
        assertEquals(2022, result.items[0].year)
        assertEquals("/covers/plex/tv-shows/10/poster.jpg", result.items[0].coverUrl)
        verify { query.setParameter("limitPlusOne", 6) }
    }

    @Test
    fun `currently watching should map rows`() {
        every { query.resultList } returns
            listOf(
                arrayOf(
                    29L,
                    "O Cavaleiro dos Sete Reinos",
                    "A Knight of the Seven Kingdoms",
                    "a-knight-of-the-seven-kingdoms",
                    2026,
                    "/covers/plex/tv-shows/29/poster.jpg",
                    6L,
                    4L,
                    1L,
                    0L,
                    Timestamp.from(Instant.parse("2026-04-03T23:02:53Z")),
                ),
            )

        val activeSince = Instant.parse("2026-01-04T00:00:00Z")
        val result = repository.currentlyWatching(limit = 10, activeSince = activeSince)

        assertEquals(1, result.size)
        assertEquals(29L, result[0].showId)
        assertEquals(6L, result[0].progress.episodesCount)
        assertEquals(4L, result[0].progress.watchedEpisodesCount)
        assertEquals(true, result[0].progress.inProgress)
        verify { query.setParameter("activeSince", activeSince) }
        verify { query.setParameter("limit", 10) }
        assertTrue(sqls.any { it.contains("progress_stats.watched_episodes_count < progress_stats.episodes_count") })
        assertTrue(sqls.any { it.contains("progress_stats.last_watched_at >= :activeSince") })
    }

    @Test
    fun `summary should map counters`() {
        var singleCall = 0
        every { query.singleResult } answers {
            singleCall++
            if (singleCall == 1) 7L else 3L
        }

        val start = Instant.parse("2026-01-01T00:00:00Z")
        val end = Instant.parse("2026-02-01T00:00:00Z")

        val summary = repository.summary(start, end)

        assertEquals(7L, summary.watchesCount)
        assertEquals(3L, summary.uniqueShowsCount)
        assertEquals(start, summary.range.start)
        assertEquals(end, summary.range.end)
        verify(exactly = 2) { query.setParameter("s", start) }
        verify(exactly = 2) { query.setParameter("e", end) }
    }

    @Test
    fun `search should include slug filter`() {
        every { query.resultList } returns emptyList<Any>()

        repository.search("sever", 10)

        val searchSql = sqls.last()
        assertTrue(searchSql.contains("LOWER(COALESCE(s.slug, '')) LIKE :q"))
        verify(exactly = 1) { query.setParameter("q", "%sever%") }
        verify(exactly = 1) { query.setParameter("n", 10) }
    }

    @Test
    fun `details by slug should resolve id and delegate to details query`() {
        every { query.resultList } returnsMany
            listOf(
                listOf(10L),
                listOf(
                    arrayOf(
                        10L,
                        "Ruptura",
                        "Severance",
                        "severance",
                        2022,
                        "desc",
                        "/covers/plex/tv-shows/10/poster.jpg",
                    ),
                ),
                emptyList<Any>(),
                emptyList<Any>(),
                emptyList<Any>(),
                emptyList<Any>(),
            )

        val response = repository.getShowDetailsBySlug("severance")

        assertEquals(10L, response.showId)
        assertEquals(0L, response.progress?.episodesCount)
        verify(exactly = 1) { query.setParameter("slug", "severance") }
    }

    @Test
    fun `season details by slug should map episodes and progress`() {
        every { query.resultList } returnsMany
            listOf(
                listOf(
                    arrayOf(
                        10L,
                        "the-big-bang-theory",
                        "The Big Bang Theory",
                        "The Big Bang Theory",
                        2007,
                        "/covers/show.jpg",
                    ),
                ),
                listOf(
                    arrayOf(
                        99L,
                        "Pilot",
                        1,
                        "Temporada 1",
                        1,
                        "Leonard and Sheldon meet Penny.",
                        1320000,
                        java.sql.Date.valueOf("2007-09-24"),
                        1L,
                        Timestamp.from(Instant.parse("2024-01-03T14:35:00Z")),
                    ),
                    arrayOf(
                        100L,
                        "The Big Bran Hypothesis",
                        1,
                        "Temporada 1",
                        2,
                        null,
                        1260000,
                        java.sql.Date.valueOf("2007-10-01"),
                        0L,
                        null,
                    ),
                ),
            )
        every { query.singleResult } returns "Temporada 1"

        val response = repository.getShowSeasonDetailsBySlug("the-big-bang-theory", 1)

        assertEquals(10L, response.showId)
        assertEquals("Temporada 1", response.seasonTitle)
        assertEquals(2L, response.episodesCount)
        assertEquals(1L, response.watchedEpisodesCount)
        assertEquals(false, response.completed)
        assertEquals(2, response.episodes.size)
        assertEquals("Pilot", response.episodes.first().title)
        assertEquals(java.time.LocalDate.parse("2007-09-24"), response.episodes.first().originallyAvailableAt)
        assertEquals(Instant.parse("2024-01-03T14:35:00Z"), response.lastWatchedAt)
        verify(exactly = 1) { query.setParameter("slug", "the-big-bang-theory") }
        verify(exactly = 2) { query.setParameter("showId", 10L) }
        verify(exactly = 2) { query.setParameter("seasonNumber", 1) }
    }

    @Test
    fun `details should count unique episodes in progress when there are rewatches`() {
        every { query.resultList } returnsMany
            listOf(
                listOf(
                    arrayOf(
                        29L,
                        "O Cavaleiro dos Sete Reinos",
                        "A Knight of the Seven Kingdoms",
                        "a-knight-of-the-seven-kingdoms",
                        2026,
                        "desc",
                        "/covers/plex/tv-shows/29/poster.jpg",
                    ),
                ),
                emptyList<Any>(),
                emptyList<Any>(),
                listOf(
                    arrayOf(1, "Temporada 1", 6L, 6L, Timestamp.from(Instant.parse("2026-04-03T23:14:52Z"))),
                ),
                emptyList<Any>(),
            )

        val response = repository.getShowDetails(29L)

        assertEquals(6L, response.progress?.episodesCount)
        assertEquals(6L, response.progress?.watchedEpisodesCount)
        assertEquals(true, response.progress?.completed)
        assertEquals(1, response.seasons.size)
        assertEquals(6L, response.seasons.first().episodesCount)
        assertEquals(6L, response.seasons.first().watchedEpisodesCount)
    }

    @Test
    fun `by year should map stats watched and unwatched`() {
        every { query.singleResult } returns arrayOf(8L, 3L)
        every { query.resultList } returnsMany
            listOf(
                listOf(
                    arrayOf(
                        10L,
                        "severance",
                        "Ruptura",
                        "Severance",
                        2022,
                        "/covers/plex/tv-shows/10/poster.jpg",
                        2L,
                        Timestamp.from(Instant.parse("2026-01-10T21:00:00Z")),
                        Timestamp.from(Instant.parse("2026-02-27T19:40:19Z")),
                    ),
                ),
                listOf(
                    arrayOf(
                        9L,
                        "dark",
                        "Dark",
                        "Dark",
                        2017,
                        "/covers/plex/tv-shows/9/poster.jpg",
                    ),
                ),
            )

        val start = Instant.parse("2026-01-01T00:00:00Z")
        val end = Instant.parse("2026-12-31T23:59:59Z")
        val response = repository.byYear(year = 2026, start = start, end = end, limitWatched = 200, limitUnwatched = 120)

        assertEquals(2026, response.year)
        assertEquals(8L, response.stats.watchesCount)
        assertEquals(3L, response.stats.uniqueShowsCount)
        assertEquals(5L, response.stats.rewatchesCount)
        assertEquals(1, response.watched.size)
        assertEquals(2L, response.watched.first().watchCountInYear)
        assertEquals(1, response.unwatched.size)
        assertEquals(9L, response.unwatched.first().showId)
        assertTrue(sqls.any { it.contains("COUNT(DISTINCT te.show_id)") })
        assertTrue(sqls.any { it.contains("ORDER BY MAX(tew.watched_at) DESC, title ASC") })
        assertTrue(sqls.any { it.contains("WHERE NOT EXISTS") })
        verify(exactly = 1) { query.setParameter("limitWatched", 200) }
        verify(exactly = 1) { query.setParameter("limitUnwatched", 120) }
    }

    @Test
    fun `by year without data should return empty lists and zero rewatches`() {
        every { query.singleResult } returns arrayOf(0L, 0L)
        every { query.resultList } returnsMany listOf(emptyList<Any>(), emptyList<Any>())

        val start = Instant.parse("2025-01-01T00:00:00Z")
        val end = Instant.parse("2025-12-31T23:59:59Z")
        val response = repository.byYear(year = 2025, start = start, end = end, limitWatched = 10, limitUnwatched = 10)

        assertEquals(0L, response.stats.watchesCount)
        assertEquals(0L, response.stats.uniqueShowsCount)
        assertEquals(0L, response.stats.rewatchesCount)
        assertTrue(response.watched.isEmpty())
        assertTrue(response.unwatched.isEmpty())
    }

    @Test
    fun `stats should aggregate totals years and unwatched`() {
        var singleCall = 0
        every { query.singleResult } answers {
            singleCall++
            when (singleCall) {
                1 -> arrayOf(120L, 85L)
                2 -> 240L
                else ->
                    arrayOf(
                        Timestamp.from(Instant.parse("2026-02-27T19:40:19Z")),
                        Timestamp.from(Instant.parse("2024-01-10T11:00:00Z")),
                    )
            }
        }
        every { query.resultList } returns
            listOf(
                arrayOf(2026, 42L, 30L),
                arrayOf(2025, 18L, 16L),
            )

        val response = repository.stats()

        assertEquals(120L, response.total.watchesCount)
        assertEquals(85L, response.total.uniqueShowsCount)
        assertEquals(240L, response.unwatchedCount)
        assertEquals(2, response.years.size)
        assertEquals(2026, response.years[0].year)
        assertEquals(12L, response.years[0].rewatchesCount)
        assertEquals(2025, response.years[1].year)
        assertEquals(2L, response.years[1].rewatchesCount)
        assertEquals(Instant.parse("2026-02-27T19:40:19Z"), response.latestWatchAt)
        assertEquals(Instant.parse("2024-01-10T11:00:00Z"), response.firstWatchAt)
        assertTrue(sqls.any { it.contains("COUNT(DISTINCT te.show_id)") })
        assertTrue(sqls.any { it.contains("WHERE NOT EXISTS") })
        assertTrue(sqls.any { it.contains("EXTRACT(YEAR FROM (tew.watched_at AT TIME ZONE 'UTC'))") })
        assertTrue(sqls.any { it.contains("ORDER BY year DESC") })
        assertTrue(sqls.any { it.contains("MAX(tew.watched_at), MIN(tew.watched_at)") })
    }

    @Test
    fun `stats without watches should return empty years and null bounds`() {
        var singleCall = 0
        every { query.singleResult } answers {
            singleCall++
            when (singleCall) {
                1 -> arrayOf(0L, 0L)
                2 -> 10L
                else -> arrayOf<Any?>(null, null)
            }
        }
        every { query.resultList } returns emptyList<Any>()

        val response = repository.stats()

        assertEquals(0L, response.total.watchesCount)
        assertEquals(0L, response.total.uniqueShowsCount)
        assertEquals(10L, response.unwatchedCount)
        assertTrue(response.years.isEmpty())
        assertNull(response.latestWatchAt)
        assertNull(response.firstWatchAt)
    }
}
