package dev.marcal.mediapulse.server.repository

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AlbumMergeRepositoryTest {
    @Test
    fun `merge binds track creation instant as a JDBC timestamp`() {
        val jdbc = mockk<NamedParameterJdbcTemplate>(relaxed = true)
        val batch = slot<Array<MapSqlParameterSource>>()
        every { jdbc.batchUpdate(any(), capture(batch)) } returns intArrayOf(1)
        val repository = AlbumMergeRepository(jdbc)
        val createdAt = Instant.parse("2026-08-25T12:00:00Z")

        repository.merge(
            targetId = 1,
            sourceIds = listOf(2),
            artistId = 3,
            ratingAlbumId = null,
            trackLinks =
                listOf(
                    AlbumMergeRepository.TrackLink(
                        albumId = 2,
                        trackId = 4,
                        discNumber = 1,
                        trackNumber = 2,
                        createdAt = createdAt,
                    ),
                ),
            migratedTrackLinks = 1,
        )

        val boundCreatedAt = batch.captured.single().getValue("createdAt")
        assertIs<Timestamp>(boundCreatedAt)
        assertEquals(createdAt, boundCreatedAt.toInstant())
    }
}
