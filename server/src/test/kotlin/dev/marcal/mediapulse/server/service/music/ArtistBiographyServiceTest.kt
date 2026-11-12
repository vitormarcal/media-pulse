package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.api.music.ArtistBiographyDto
import dev.marcal.mediapulse.server.api.music.WikipediaIntroductionDto
import dev.marcal.mediapulse.server.integration.musicbrainz.MusicBrainzApiClient
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbArtistCandidate
import dev.marcal.mediapulse.server.integration.wikimedia.WikipediaIntroductionClient
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.repository.ArtistBiographyRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ArtistBiographyServiceTest {
    private val artist = Artist(id = 1, name = "Artist", fingerprint = "artist", musicbrainzArtistId = "artist-mbid")
    private val artists = mockk<ArtistRepository> { every { findById(1) } returns Optional.of(artist) }
    private val mb = mockk<MusicBrainzApiClient> { coEvery { getArtist("artist-mbid") } returns MbArtistCandidate("artist-mbid", "Artist") }
    private val introduction =
        WikipediaIntroductionDto(
            "Biografia",
            "pt",
            "https://pt.wikipedia.org/wiki/Artist",
            42L,
            "CC BY-SA 4.0",
            "https://creativecommons.org/licenses/by-sa/4.0/",
        )
    private val wikipedia =
        mockk<WikipediaIntroductionClient> {
            coEvery { introduction(any()) } returns
                this@ArtistBiographyServiceTest.introduction
        }
    private val biographies =
        mockk<ArtistBiographyRepository>(relaxed = true) {
            every { find(1) } returns null
            every { lockArtist(1) } returns "artist-mbid"
        }
    private val tx = mockk<TxUtil> { every { inTx<Any>(any()) } answers { firstArg<() -> Any>().invoke() } }
    private val service = ArtistBiographyService(artists, mb, wikipedia, biographies, tx)

    @Test
    fun `preview does not write and confirmation saves exactly the reviewed introduction`() =
        runBlocking {
            val preview = service.preview(1)
            verify(exactly = 0) { biographies.save(any(), any()) }
            assertEquals(preview.proposed, service.apply(1, preview.token!!))
            verify { biographies.save(1, preview.proposed!!) }
            coVerify(exactly = 1) { mb.getArtist("artist-mbid") }
            coVerify(exactly = 1) { wikipedia.introduction(any()) }
            assertFailsWith<ResponseStatusException> { service.apply(1, preview.token!!) }
            Unit
        }

    @Test
    fun `missing article preserves saved text and does not issue an apply token`() =
        runBlocking {
            val saved = ArtistBiographyDto("artist-mbid", introduction, Instant.now())
            every { biographies.find(1) } returns saved
            coEvery { wikipedia.introduction(any()) } returns null
            val result = service.preview(1)
            assertEquals(saved, result.current)
            assertNull(result.proposed)
            assertNull(result.token)
            verify(exactly = 0) { biographies.save(any(), any()) }
        }

    @Test
    fun `changed association rejects an otherwise valid preview`() =
        runBlocking {
            val preview = service.preview(1)
            every { biographies.lockArtist(1) } returns "another-artist"
            assertFailsWith<ResponseStatusException> { service.apply(1, preview.token!!) }
            verify(exactly = 0) { biographies.save(any(), any()) }
        }

    @Test
    fun `concurrent text update rejects stale confirmation`() =
        runBlocking {
            val preview = service.preview(1)
            every { biographies.find(1) } returns preview.proposed
            assertFailsWith<ResponseStatusException> { service.apply(1, preview.token!!) }
            verify(exactly = 0) { biographies.save(any(), any()) }
        }

    @Test
    fun `wrong artist and unknown token cannot write`() =
        runBlocking {
            val preview = service.preview(1)
            assertFailsWith<ResponseStatusException> { service.apply(2, preview.token!!) }
            assertFailsWith<ResponseStatusException> { service.apply(1, "unknown") }
            verify(exactly = 0) { biographies.save(any(), any()) }
        }

    @Test
    fun `provider failure and missing association cannot overwrite a biography`() =
        runBlocking {
            coEvery { wikipedia.introduction(any()) } throws IllegalStateException("unavailable")
            assertEquals(502, assertFailsWith<ResponseStatusException> { service.preview(1) }.statusCode.value())
            every { artists.findById(1) } returns Optional.of(artist.copy(musicbrainzArtistId = null))
            assertEquals(409, assertFailsWith<ResponseStatusException> { service.preview(1) }.statusCode.value())
            verify(exactly = 0) { biographies.save(any(), any()) }
        }
}
