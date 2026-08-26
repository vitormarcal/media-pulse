package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.repository.AlbumMergeRepository
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlbumTrackOrderPlannerTest {
    @Test
    fun `preserves base positions and clears additional conflicts`() {
        val plan =
            AlbumTrackOrderPlanner.plan(
                links =
                    listOf(
                        link(albumId = 1, trackId = 10, trackNumber = 1),
                        link(albumId = 1, trackId = 20, trackNumber = 2),
                        link(albumId = 2, trackId = 30, trackNumber = 2),
                        link(albumId = 2, trackId = 40, trackNumber = 3),
                    ),
                baseAlbumId = 1,
            )

        assertEquals(3, plan.positionedTrackCount)
        assertEquals(1, plan.unpositionedTrackCount)
        assertEquals(1, plan.conflictedTrackCount)
        assertNull(plan.links.single { it.trackId == 30L }.discNumber)
        assertNull(plan.links.single { it.trackId == 30L }.trackNumber)
        assertEquals(3, plan.links.single { it.trackId == 40L }.trackNumber)
    }

    @Test
    fun `uses the selected album position for a track shared by editions`() {
        val plan =
            AlbumTrackOrderPlanner.plan(
                links =
                    listOf(
                        link(albumId = 1, trackId = 10, trackNumber = 1),
                        link(albumId = 2, trackId = 10, discNumber = 2, trackNumber = 7),
                        link(albumId = 1, trackId = 20, discNumber = null, trackNumber = null),
                    ),
                baseAlbumId = 2,
            )

        val sharedTrack = plan.links.single { it.trackId == 10L }
        assertEquals(2, sharedTrack.albumId)
        assertEquals(2, sharedTrack.discNumber)
        assertEquals(7, sharedTrack.trackNumber)
    }

    @Test
    fun `prefers a complete additional position and resolves equal slots deterministically`() {
        val links =
            listOf(
                link(albumId = 3, trackId = 30, trackNumber = 4),
                link(albumId = 2, trackId = 20, trackNumber = 4),
                link(albumId = 2, trackId = 10, discNumber = null, trackNumber = null),
                link(albumId = 3, trackId = 10, trackNumber = 5),
            )

        val first = AlbumTrackOrderPlanner.plan(links, baseAlbumId = 1)
        val reversed = AlbumTrackOrderPlanner.plan(links.reversed(), baseAlbumId = 1)

        assertEquals(first, reversed)
        assertEquals(4, first.links.single { it.trackId == 20L }.trackNumber)
        assertNull(first.links.single { it.trackId == 30L }.trackNumber)
        assertEquals(5, first.links.single { it.trackId == 10L }.trackNumber)
    }

    private fun link(
        albumId: Long,
        trackId: Long,
        discNumber: Int? = 1,
        trackNumber: Int?,
    ) = AlbumMergeRepository.TrackLink(
        albumId = albumId,
        trackId = trackId,
        discNumber = discNumber,
        trackNumber = trackNumber,
        createdAt = Instant.parse("2026-08-25T12:00:00Z"),
    )
}
