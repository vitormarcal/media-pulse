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

class MusicQueryRepositoryTest {
    private val em = mockk<EntityManager>()
    private val query = mockk<Query>(relaxed = true)
    private lateinit var repository: MusicQueryRepository

    @BeforeEach
    fun setUp() {
        repository = MusicQueryRepository(em)
        every { em.createQuery(any<String>(), any<Class<*>>()) } returns mockk(relaxed = true)
        every { em.createNativeQuery(any<String>()) } returns query
        every { query.setParameter(any<String>(), any()) } returns query
        every { query.resultList } returns emptyList<Any>()
    }

    @Test
    fun `recent albums should map rows and expose next cursor`() {
        every { query.resultList } returns
            listOf(
                arrayOf(
                    10L,
                    "Vespertine",
                    99L,
                    "Bjork",
                    2001,
                    "/covers/spotify/albums/10.jpg",
                    Timestamp.from(Instant.parse("2026-04-05T10:00:00Z")),
                    7L,
                ),
                arrayOf(
                    9L,
                    "Homogenic",
                    99L,
                    "Bjork",
                    1997,
                    "/covers/spotify/albums/9.jpg",
                    Timestamp.from(Instant.parse("2026-04-04T10:00:00Z")),
                    5L,
                ),
            )

        val result = repository.getRecentAlbums(limit = 1, cursor = null)

        assertEquals(1, result.items.size)
        assertEquals(10L, result.items.first().albumId)
        assertEquals("Vespertine", result.items.first().albumTitle)
        assertEquals("ts:1775383200000:id:10", result.nextCursor)
        verify { query.setParameter("limitPlusOne", 2) }
    }
}
