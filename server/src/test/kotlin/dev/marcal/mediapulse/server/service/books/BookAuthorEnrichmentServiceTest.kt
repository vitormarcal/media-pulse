package dev.marcal.mediapulse.server.service.books

import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.api.books.AuthorEnrichmentApply
import dev.marcal.mediapulse.server.api.books.AuthorEnrichmentDto
import dev.marcal.mediapulse.server.api.books.AuthorEnrichmentField
import dev.marcal.mediapulse.server.integration.hardcover.HardcoverApiClient
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverAuthorProfile
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverImage
import dev.marcal.mediapulse.server.repository.BookAuthorProfileRepository
import dev.marcal.mediapulse.server.service.image.LibraryImageStorageService
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BookAuthorEnrichmentServiceTest {
    private val profiles = mockk<BookAuthorProfileRepository>(relaxed = true)
    private val client = mockk<HardcoverApiClient>()
    private val images = mockk<LibraryImageStorageService>()
    private val tx = mockk<TxUtil>()
    private val service = BookAuthorEnrichmentService(profiles, client, images, tx)
    private val remote =
        HardcoverAuthorProfile(
            42,
            "Pessoa externa",
            "pessoa",
            bio = "Texto",
            bornYear = 1980,
            location = "Brasil",
            image = HardcoverImage("https://images.hardcover.app/photo.jpg"),
        )
    private val old =
        AuthorEnrichmentDto(
            42,
            "Pessoa",
            "https://hardcover.app/authors/pessoa",
            Instant.EPOCH,
            biography = "Salva",
            born = "1980",
            died = "2020",
            location = "Outra",
            photoUrl = "/covers/saved.jpg",
            photoSourceUrl = "https://images.hardcover.app/old.jpg",
        )

    init {
        every { profiles.exists(1) } returns true
        every { profiles.lock(1) } returns true
        every { profiles.find(1) } returns null
        every { profiles.owner(any()) } returns null
        every { tx.inTx<Any?>(any()) } answers { firstArg<() -> Any?>().invoke() }
        coEvery { client.fetchAuthorProfile(any(), any()) } returns remote
    }

    @Test fun `preview does not write and partial selection preserves saved fields`() =
        runBlocking {
            every { profiles.find(1) } returns old
            val preview = service.preview(1, "42")
            verify(exactly = 0) { profiles.save(any(), any()) }
            val saved = service.apply(1, AuthorEnrichmentApply(preview.token, setOf(AuthorEnrichmentField.BIOGRAPHY)))
            assertEquals("Texto", saved.biography)
            assertEquals(old.photoUrl, saved.photoUrl)
            assertEquals("Outra", saved.location)
            assertEquals(old.died, saved.died)
            assertFailsWith<ResponseStatusException> {
                service.apply(
                    1,
                    AuthorEnrichmentApply(preview.token, setOf(AuthorEnrichmentField.BIOGRAPHY)),
                )
            }
        }

    @Test fun `identity change must be confirmed and removes prior person's data`() =
        runBlocking {
            every { profiles.find(1) } returns old.copy(hardcoverId = 99)
            val preview = service.preview(1, "42")
            val request = AuthorEnrichmentApply(preview.token, setOf(AuthorEnrichmentField.LOCATION))
            assertFailsWith<ResponseStatusException> { service.apply(1, request) }
            val saved = service.apply(1, request.copy(confirmIdentityChange = true))
            assertEquals(42L, saved.hardcoverId)
            assertNull(saved.biography)
            assertNull(saved.photoUrl)
            assertNull(saved.born)
            assertEquals("Brasil", saved.location)
        }

    @Test fun `empty provider fields are unavailable and dates preserve missing values`() =
        runBlocking {
            every { profiles.find(1) } returns old
            coEvery { client.fetchAuthorProfile(any(), any()) } returns remote.copy(bio = null, image = null)
            val preview = service.preview(1, "42")
            assertFailsWith<ResponseStatusException> {
                service.apply(
                    1,
                    AuthorEnrichmentApply(preview.token, setOf(AuthorEnrichmentField.BIOGRAPHY)),
                )
            }
            val saved = service.apply(1, AuthorEnrichmentApply(preview.token, setOf(AuthorEnrichmentField.DATES)))
            assertEquals("Salva", saved.biography)
            assertEquals("2020", saved.died)
            assertEquals("1980", saved.born)
        }

    @Test fun `photo failure applies nothing and permits retry without photo`() =
        runBlocking {
            every { images.downloadProviderImage(any()) } throws IllegalStateException("offline")
            val preview = service.preview(1, "42")
            val error =
                assertFailsWith<ResponseStatusException> {
                    service.apply(
                        1,
                        AuthorEnrichmentApply(preview.token, setOf(AuthorEnrichmentField.BIOGRAPHY, AuthorEnrichmentField.PHOTO)),
                    )
                }
            assertEquals(502, error.statusCode.value())
            verify(exactly = 0) { profiles.save(any(), any()) }
            assertEquals("Texto", service.apply(1, AuthorEnrichmentApply(preview.token, setOf(AuthorEnrichmentField.BIOGRAPHY))).biography)
        }

    @Test fun `changed snapshot and conflict at commit reject stale application`() =
        runBlocking {
            val preview = service.preview(1, "42")
            every { profiles.find(1) } returns old
            assertEquals(
                409,
                assertFailsWith<ResponseStatusException> {
                    service.apply(1, AuthorEnrichmentApply(preview.token, setOf(AuthorEnrichmentField.LOCATION)))
                }.statusCode.value(),
            )
            every { profiles.find(1) } returns null
            every { profiles.owner(42) } returns 2
            assertFailsWith<ResponseStatusException> {
                service.apply(
                    1,
                    AuthorEnrichmentApply(preview.token, setOf(AuthorEnrichmentField.LOCATION)),
                )
            }
            verify(exactly = 0) { profiles.save(any(), any()) }
        }

    @Test fun `duplicate remote profile is rejected before preview`() =
        runBlocking {
            every { profiles.owner(42) } returns 2
            assertEquals(409, assertFailsWith<ResponseStatusException> { service.preview(1, "42") }.statusCode.value())
        }

    @Test fun `missing author remote absence and provider failures preserve local data`() =
        runBlocking {
            coEvery { client.fetchAuthorProfile(any(), any()) } returns null
            assertEquals(404, assertFailsWith<ResponseStatusException> { service.preview(1, "42") }.statusCode.value())
            coEvery { client.fetchAuthorProfile(any(), any()) } throws IllegalStateException("offline")
            assertEquals(502, assertFailsWith<ResponseStatusException> { service.preview(1, "42") }.statusCode.value())
            every { profiles.exists(1) } returns false
            assertEquals(404, assertFailsWith<ResponseStatusException> { service.preview(1, "42") }.statusCode.value())
            verify(exactly = 0) { profiles.save(any(), any()) }
        }

    @Test fun `reference accepts profile links and ids but not arbitrary hosts paths or credentials`() {
        assertEquals(42L to null, BookAuthorEnrichmentService.parseReference("42"))
        assertEquals(null to "akira-toriyama", BookAuthorEnrichmentService.parseReference("https://hardcover.app/authors/akira-toriyama/"))
        for (value in listOf(
            "-1",
            "0",
            "https://evil.test/authors/a",
            "https://hardcover.app/books/a",
            "https://x@hardcover.app/authors/a",
            "http://hardcover.app/authors/a",
            "https://hardcover.app/authors/a?x=1",
        )) {
            assertFailsWith<ResponseStatusException> {
                BookAuthorEnrichmentService.parseReference(value)
            }
        }
        assertTrue(BookAuthorEnrichmentService.allowedPhoto("https://images.hardcover.app/photo.jpg"))
        assertFalse(BookAuthorEnrichmentService.allowedPhoto("https://images.hardcover.app.evil.test/a.jpg"))
        assertFalse(BookAuthorEnrichmentService.allowedPhoto("http://localhost/a.jpg"))
    }

    @Test fun `links only include safe external urls`() {
        val json =
            ObjectMapper().readTree(
                """{"website":"https://example.org", "nested":[
                    {"url":"javascript:alert(1)"},{"url":"https://example.org"},{"url":"https://other.org"}]}""",
            )
        assertEquals(listOf("https://example.org", "https://other.org"), BookAuthorEnrichmentService.collectLinks(json))
    }
}
