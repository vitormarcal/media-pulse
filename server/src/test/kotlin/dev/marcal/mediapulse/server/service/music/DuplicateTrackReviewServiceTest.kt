package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.api.music.ManualTrackMergeRequest
import dev.marcal.mediapulse.server.repository.MusicDuplicateReviewRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DuplicateTrackReviewServiceTest {
    private val repository = mockk<MusicDuplicateReviewRepository>(relaxed = true)
    private val service = DuplicateTrackReviewService(repository)

    @Test
    fun `manual merge consolidates tracks that belong to the album`() {
        every { repository.findAlbumTrackIds(7, listOf(11, 12)) } returns setOf(11, 12)
        every { repository.mergeTracks(11, listOf(12)) } returns MusicDuplicateReviewRepository.MergeStats(1, 2, 3, 4)

        val response = service.manualMerge(ManualTrackMergeRequest(7, 11, listOf(12)))

        assertEquals(11L, response.targetTrackId)
        assertEquals(listOf(12L), response.mergedTrackIds)
        assertEquals(2, response.movedPlaybacks)
        verify { repository.lockAlbum(7) }
        verify { repository.mergeTracks(11, listOf(12)) }
    }

    @Test
    fun `manual merge rejects tracks outside the album`() {
        every { repository.findAlbumTrackIds(7, listOf(11, 99)) } returns setOf(11)

        assertFailsWith<ResponseStatusException> {
            service.manualMerge(ManualTrackMergeRequest(7, 11, listOf(99)))
        }

        verify(exactly = 0) { repository.mergeTracks(any(), any()) }
    }
}
