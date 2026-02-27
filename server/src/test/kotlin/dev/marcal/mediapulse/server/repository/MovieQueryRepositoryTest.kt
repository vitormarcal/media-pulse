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
}
