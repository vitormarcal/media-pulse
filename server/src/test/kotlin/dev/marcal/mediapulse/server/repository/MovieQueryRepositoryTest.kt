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
import kotlin.test.assertTrue

class MovieQueryRepositoryTest {
    private val em = mockk<EntityManager>()
    private val query = mockk<Query>(relaxed = true)
    private lateinit var repository: MovieQueryRepository
    private val sqls = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        repository = MovieQueryRepository(em)
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
                    "De Olhos Bem Fechados",
                    "Eyes Wide Shut",
                    "eyes-wide-shut",
                    1999,
                    "/covers/plex/movies/10/poster.jpg",
                    Timestamp.from(Instant.parse("2026-02-26T10:00:00Z")),
                ),
            )

        val result = repository.recent(5)

        assertEquals(1, result.size)
        assertEquals(10L, result[0].movieId)
        assertEquals("De Olhos Bem Fechados", result[0].title)
        assertEquals("Eyes Wide Shut", result[0].originalTitle)
        assertEquals("eyes-wide-shut", result[0].slug)
        assertEquals(1999, result[0].year)
        assertEquals("/covers/plex/movies/10/poster.jpg", result[0].coverUrl)
        verify { query.setParameter("n", 5) }
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
        assertEquals(3L, summary.uniqueMoviesCount)
        assertEquals(start, summary.range.start)
        assertEquals(end, summary.range.end)
        verify(exactly = 2) { query.setParameter("s", start) }
        verify(exactly = 2) { query.setParameter("e", end) }
    }

    @Test
    fun `search should include slug filter`() {
        every { query.resultList } returns emptyList<Any>()

        repository.search("3828", 10)

        val searchSql = sqls.last()
        assertTrue(searchSql.contains("LOWER(COALESCE(m.slug, '')) LIKE :q"))
        verify(exactly = 1) { query.setParameter("q", "%3828%") }
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
                        "De Olhos Bem Fechados",
                        "Eyes Wide Shut",
                        "eyes-wide-shut",
                        1999,
                        "desc",
                        "/covers/plex/movies/10/poster.jpg",
                    ),
                ),
                emptyList<Any>(),
                emptyList<Any>(),
            )

        val response = repository.getMovieDetailsBySlug("3828")

        assertEquals(10L, response.movieId)
        verify(exactly = 1) { query.setParameter("slug", "3828") }
    }

    @Test
    fun `by year should map stats watched and unwatched`() {
        every { query.singleResult } returns arrayOf(8L, 3L)
        every { query.resultList } returnsMany
            listOf(
                listOf(
                    arrayOf(
                        10L,
                        "eyes-wide-shut",
                        "De Olhos Bem Fechados",
                        "Eyes Wide Shut",
                        1999,
                        "/covers/plex/movies/10/poster.jpg",
                        2L,
                        Timestamp.from(Instant.parse("2026-01-10T21:00:00Z")),
                        Timestamp.from(Instant.parse("2026-02-27T19:40:19Z")),
                    ),
                ),
                listOf(
                    arrayOf(
                        9L,
                        "movie-x",
                        "Movie X",
                        "Movie X",
                        2001,
                        "/covers/plex/movies/9/poster.jpg",
                    ),
                ),
            )

        val start = Instant.parse("2026-01-01T00:00:00Z")
        val end = Instant.parse("2026-12-31T23:59:59Z")
        val response = repository.byYear(year = 2026, start = start, end = end, limitWatched = 200, limitUnwatched = 120)

        assertEquals(2026, response.year)
        assertEquals(8L, response.stats.watchesCount)
        assertEquals(3L, response.stats.uniqueMoviesCount)
        assertEquals(5L, response.stats.rewatchesCount)
        assertEquals(1, response.watched.size)
        assertEquals(2L, response.watched.first().watchCountInYear)
        assertEquals(1, response.unwatched.size)
        assertEquals(9L, response.unwatched.first().movieId)
        assertTrue(sqls.any { it.contains("COUNT(DISTINCT mw.movie_id)") })
        assertTrue(sqls.any { it.contains("ORDER BY MAX(mw.watched_at) DESC, title ASC") })
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
        assertEquals(0L, response.stats.uniqueMoviesCount)
        assertEquals(0L, response.stats.rewatchesCount)
        assertTrue(response.watched.isEmpty())
        assertTrue(response.unwatched.isEmpty())
    }
}
