package dev.marcal.mediapulse.server.repository

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MusicQueryRepositoryTest {
    private val em = mockk<EntityManager>()
    private val mediaCommentQueryRepository = mockk<MediaCommentQueryRepository>()
    private val mediaRatingQueryRepository = mockk<MediaRatingQueryRepository>()
    private val query = mockk<Query>(relaxed = true)
    private lateinit var repository: MusicQueryRepository

    @BeforeEach
    fun setUp() {
        repository = MusicQueryRepository(em, mediaCommentQueryRepository, mediaRatingQueryRepository)
        every { em.createQuery(any<String>(), any<Class<*>>()) } returns mockk(relaxed = true)
        every { em.createNativeQuery(any<String>()) } returns query
        every { query.setParameter(any<String>(), any()) } returns query
        every { query.resultList } returns emptyList<Any>()
        every { mediaCommentQueryRepository.findByEntity(any(), any()) } returns emptyList()
        every { mediaRatingQueryRepository.findByEntity(any(), any()) } returns null
        every { mediaRatingQueryRepository.findByEntities(any(), any<Collection<Long>>()) } returns emptyMap()
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

    @Test
    fun `rediscovered albums should map rows and bind heuristic thresholds`() {
        val sql = slot<String>()
        every { em.createNativeQuery(capture(sql)) } returns query
        every { query.resultList } returns
            listOf(
                arrayOf(
                    10L,
                    "Vespertine",
                    99L,
                    "Bjork",
                    2001,
                    "/covers/spotify/albums/10.jpg",
                    12L,
                    3L,
                    Timestamp.from(Instant.parse("2025-12-01T10:00:00Z")),
                    Timestamp.from(Instant.parse("2026-04-05T10:00:00Z")),
                    Timestamp.from(Instant.parse("2026-04-08T10:00:00Z")),
                    125L,
                ),
            )

        val result = repository.getRediscoveredAlbums(limit = 8)

        assertEquals(1, result.size)
        assertEquals(10L, result.first().albumId)
        assertEquals(12L, result.first().historicalPlayCount)
        assertEquals(3L, result.first().recentPlayCount)
        assertEquals(125L, result.first().quietGapDays)
        assertTrue(sql.captured.contains("COUNT(tp.id) FILTER (WHERE tp.played_at < :recentStart)"))
        assertTrue(sql.captured.contains("recent_play_count >= :minRecentPlays"))
        assertTrue(sql.captured.contains("EXTRACT(EPOCH FROM (first_recent_play - last_historical_play)) >= :minGapSeconds"))
        assertTrue(sql.captured.contains("quiet_gap_days * LN(historical_play_count + 1) * LN(recent_play_count + 1) DESC"))
        assertTrue(sql.captured.contains("historical_play_count DESC"))
        verify { query.setParameter("minHistoricalPlays", 5L) }
        verify { query.setParameter("minRecentPlays", 2L) }
        verify { query.setParameter("minGapSeconds", 7776000L) }
        verify { query.setParameter("limit", 8) }
    }
}
