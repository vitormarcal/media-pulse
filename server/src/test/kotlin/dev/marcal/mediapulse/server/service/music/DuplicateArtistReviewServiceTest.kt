package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.api.music.ArtistMergeCandidateResponse
import dev.marcal.mediapulse.server.api.music.ArtistMergePreviewRequest
import dev.marcal.mediapulse.server.api.music.ArtistMergeRequest
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.repository.ArtistMergeRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DuplicateArtistReviewServiceTest {
    private val repository = mockk<ArtistMergeRepository>()
    private val artists = mockk<ArtistRepository>()
    private val service = DuplicateArtistReviewService(repository, artists)

    @Test
    fun `preview sums the data that will move without merging albums`() {
        every { repository.findCandidates(listOf(2L, 1L)) } returns
            listOf(candidate(1, "Björk", 4, 40, 100), candidate(2, "Bjork", 2, 20, 30))

        val result = service.preview(ArtistMergePreviewRequest(1, listOf(2)))

        assertEquals(6, result.totalAlbums)
        assertEquals(60, result.totalTracks)
        assertEquals(130, result.totalPlaybacks)
    }

    @Test
    fun `merge rejects divergent external identifiers before writing`() {
        every { repository.findCandidates(listOf(2L, 1L)) } returns
            listOf(
                candidate(1, "Artist", 1, 1, 1, spotifyId = "one"),
                candidate(2, "Artist 2", 1, 1, 1, spotifyId = "two"),
            )

        assertFailsWith<ResponseStatusException> {
            service.merge(ArtistMergeRequest(1, listOf(2)))
        }
    }

    @Test
    fun `merge accepts previous target name as alias and uses snapshot read after lock`() {
        val initial = listOf(candidate(1, "Old target", 1, 1, 1), candidate(2, "Chosen name", 1, 1, 1))
        val fresh = listOf(candidate(1, "Old target", 2, 3, 4), candidate(2, "Fresh chosen name", 2, 3, 4))
        every { repository.findCandidates(listOf(2L, 1L)) } returnsMany listOf(initial, fresh)
        every { repository.lockArtists(setOf(1L, 2L)) } returns Unit
        val command = slot<ArtistMergeRepository.MergeCommand>()
        every { repository.merge(capture(command)) } returns ArtistMergeRepository.MergeStats(1, 1, 0, 0, 1)
        every { artists.findById(1) } returns Optional.of(Artist(id = 1, name = "Fresh chosen name", fingerprint = "fingerprint"))

        service.merge(
            ArtistMergeRequest(
                targetArtistId = 1,
                sourceArtistIds = listOf(2),
                nameFromArtistId = 2,
                imageFromArtistId = 1,
                musicBrainzFromArtistId = 1,
                ratingFromArtistId = null,
                preserveAliasArtistIds = listOf(1),
            ),
        )

        assertEquals(listOf(1L), command.captured.aliasIds)
        assertEquals(
            dev.marcal.mediapulse.server.util.FingerprintUtil
                .artistFp("Fresh chosen name"),
            command.captured.fingerprint,
        )
    }

    private fun candidate(
        id: Long,
        name: String,
        albums: Long,
        tracks: Long,
        plays: Long,
        spotifyId: String? = null,
    ) = ArtistMergeCandidateResponse(
        id,
        name,
        null,
        spotifyId,
        null,
        null,
        null,
        null,
        albums,
        tracks,
        plays,
        null,
        null,
        emptyList(),
    )
}
