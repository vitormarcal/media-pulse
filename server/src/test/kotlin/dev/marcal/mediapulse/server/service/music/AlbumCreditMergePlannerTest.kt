package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.api.music.AlbumArtistDto
import dev.marcal.mediapulse.server.api.music.AlbumMergeCandidateResponse
import dev.marcal.mediapulse.server.model.music.AlbumArtistSource
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlbumCreditMergePlannerTest {
    private fun album(
        id: Long,
        source: AlbumArtistSource,
        name: String = "Artist",
    ) = AlbumMergeCandidateResponse(
        id,
        "Album",
        null,
        null,
        0,
        0,
        null,
        emptyList(),
        emptyList(),
        null,
        null,
        listOf(AlbumArtistDto(7, "Artist", 0, name, "", source)),
    )

    @Test
    fun `confirmed credits take priority over automatic credits and require explicit choice when names differ`() {
        val spotify = album(1, AlbumArtistSource.SPOTIFY)
        val confirmed = album(2, AlbumArtistSource.MUSICBRAINZ_CONFIRMED, "Artist alias")
        val recommended = AlbumCreditMergePlanner.plan(listOf(spotify, confirmed), 1, null)
        assertEquals(2, recommended.fromAlbumId)
        assertTrue(recommended.requiresChoice)
        val explicit = AlbumCreditMergePlanner.plan(listOf(spotify, confirmed), 1, 1)
        assertEquals(1, explicit.fromAlbumId)
        assertEquals(spotify.artists, explicit.artists)
    }

    @Test
    fun `equal credits across origins need no choice and unrelated selection is rejected`() {
        val candidates = listOf(album(1, AlbumArtistSource.LEGACY), album(2, AlbumArtistSource.SPOTIFY))
        assertFalse(AlbumCreditMergePlanner.plan(candidates, 1, null).requiresChoice)
        assertFailsWith<ResponseStatusException> { AlbumCreditMergePlanner.plan(candidates, 1, 99) }
    }
}
