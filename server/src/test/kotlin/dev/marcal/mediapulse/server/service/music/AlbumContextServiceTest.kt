package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.integration.musicbrainz.MusicBrainzApiClient
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbReleaseGroupCandidate
import dev.marcal.mediapulse.server.integration.wikimedia.AlbumWikipediaClient
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.repository.AlbumContextRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AlbumContextServiceTest {
    private val album =
        Album(id = 7, artistId = 1, title = "Record", titleKey = "record", fingerprint = "record", musicbrainzReleaseGroupId = "rg")
    private val albums = mockk<AlbumRepository> { every { findById(7) } returns Optional.of(album) }
    private val mb =
        mockk<MusicBrainzApiClient> {
            coEvery { getReleaseGroup("rg") } returns
                MbReleaseGroupCandidate("rg", "Record", primaryType = "Album")
        }
    private val wiki =
        mockk<AlbumWikipediaClient> {
            coEvery { introduction(any()) } returns null
            every { externalLinks(any(), "rg") } returns mapOf("MusicBrainz" to "https://musicbrainz.org/release-group/rg")
        }
    private val repository =
        mockk<AlbumContextRepository>(relaxed = true) {
            every { find(7) } returns null
            every { lockAlbum(7) } returns "rg"
        }
    private val tx = mockk<TxUtil> { every { inTx<Any>(any()) } answers { firstArg<() -> Any>().invoke() } }
    private val service = AlbumContextService(albums, mb, wiki, repository, tx)

    @Test
    fun `absence of a new introduction preserves the saved introduction for the same association`() =
        runBlocking {
            val intro =
                dev.marcal.mediapulse.server.api.music.AlbumIntroductionDto(
                    "Saved introduction",
                    "pt",
                    "https://pt.wikipedia.org/wiki/Record",
                    42L,
                    "CC BY-SA 4.0",
                    "https://creativecommons.org/licenses/by-sa/4.0/",
                )
            val current =
                dev.marcal.mediapulse.server.api.music.AlbumContextDto(
                    "rg",
                    introduction = intro,
                    fetchedAt = java.time.Instant.now(),
                )
            every { repository.find(7) } returns current
            assertEquals(intro, service.preview(7).proposed.introduction)
            verify(exactly = 0) { repository.save(any(), any()) }
        }

    @Test
    fun `preview does not write and apply saves exact snapshot without another remote call`() =
        runBlocking {
            val preview = service.preview(7)
            verify(exactly = 0) { repository.save(any(), any()) }
            assertEquals(preview.proposed, service.apply(7, preview.token))
            verify { repository.save(7, preview.proposed) }
            coVerify(exactly = 1) { mb.getReleaseGroup("rg") }
            coVerify(exactly = 1) { wiki.introduction(any()) }
            assertFailsWith<ResponseStatusException> { service.apply(7, preview.token) }
            Unit
        }

    @Test
    fun `wrong album and changed association reject confirmation`() =
        runBlocking {
            val preview = service.preview(7)
            assertFailsWith<ResponseStatusException> { service.apply(8, preview.token) }
            every { repository.lockAlbum(7) } returns "another-group"
            assertFailsWith<ResponseStatusException> { service.apply(7, preview.token) }
            verify(exactly = 0) { repository.save(any(), any()) }
        }

    @Test
    fun `concurrent context update rejects stale preview`() =
        runBlocking {
            val preview = service.preview(7)
            every { repository.find(7) } returns preview.proposed
            assertFailsWith<ResponseStatusException> { service.apply(7, preview.token) }
            verify(exactly = 0) { repository.save(any(), any()) }
        }

    @Test
    fun `provider failure cannot overwrite saved content`() =
        runBlocking {
            coEvery { wiki.introduction(any()) } throws IllegalStateException("upstream down")
            assertFailsWith<ResponseStatusException> { service.preview(7) }
            verify(exactly = 0) { repository.save(any(), any()) }
        }

    @Test
    fun `unlinked album is rejected without contacting providers`() =
        runBlocking {
            every { albums.findById(7) } returns Optional.of(album.copy(musicbrainzReleaseGroupId = null))
            assertFailsWith<ResponseStatusException> { service.preview(7) }
            coVerify(exactly = 0) { mb.getReleaseGroup(any()) }
        }
}
