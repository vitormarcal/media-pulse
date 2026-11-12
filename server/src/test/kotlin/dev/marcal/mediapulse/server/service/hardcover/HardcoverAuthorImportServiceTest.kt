package dev.marcal.mediapulse.server.service.hardcover

import dev.marcal.mediapulse.server.api.books.AuthorEnrichmentDto
import dev.marcal.mediapulse.server.integration.hardcover.HardcoverApiClient
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverAuthorProfile
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverImage
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.repository.BookAuthorProfileRepository
import dev.marcal.mediapulse.server.repository.hardcover.HardcoverAuthorCandidateRepository
import dev.marcal.mediapulse.server.service.image.LibraryImageStorageService
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HardcoverAuthorImportServiceTest {
    private val candidates = mockk<HardcoverAuthorCandidateRepository>(relaxed = true)
    private val profiles = mockk<BookAuthorProfileRepository>(relaxed = true)
    private val api = mockk<HardcoverApiClient>()
    private val images = mockk<LibraryImageStorageService>()
    private val tx = mockk<TxUtil>()
    private val service = HardcoverAuthorImportService(candidates, profiles, api, images, tx)
    private val remote =
        HardcoverAuthorProfile(
            42,
            "Pessoa",
            "pessoa",
            bio = "Biografia",
            bornYear = 1980,
            location = "Brasil",
            image = HardcoverImage("https://assets.hardcover.app/photo.png"),
        )

    init {
        every { profiles.lock(any()) } returns true
        every { profiles.isManual(any()) } returns false
        every { profiles.find(any()) } returns null
        every { profiles.owner(any()) } returns null
        every { candidates.uniqueIdentity(any()) } returns 42L
        every { candidates.claim(any(), any()) } returns true
        every { tx.inTx<Any?>(any()) } answers { firstArg<() -> Any?>().invoke() }
        coEvery { api.fetchAuthorProfile(42, null) } returns remote
        every { images.downloadProviderImage(any()) } returns ImageContent(byteArrayOf(1), null)
        every { images.saveBookAuthor(any(), any(), any()) } returns "/covers/portrait.png"
    }

    @Test fun `safe unique contribution automatically saves local portrait and profile once per batch`() =
        runBlocking {
            service.enrich(setOf(1, 1, 1))
            val result = slot<AuthorEnrichmentDto>()
            verify(exactly = 1) { profiles.saveAutomatically(1, capture(result)) }
            assertEquals(42L, result.captured.hardcoverId)
            assertEquals("/covers/portrait.png", result.captured.photoUrl)
            assertEquals("Biografia", result.captured.biography)
            coVerify(exactly = 1) { api.fetchAuthorProfile(42, null) }
        }

    @Test fun `manual profiles ambiguous evidence and cooldown do not query provider`() =
        runBlocking {
            every { profiles.isManual(1) } returns true
            every { candidates.uniqueIdentity(2) } returns null
            every { candidates.claim(3, 42) } returns false
            service.enrich(setOf(1, 2, 3))
            coVerify(exactly = 0) { api.fetchAuthorProfile(any(), any()) }
            verify(exactly = 0) { profiles.saveAutomatically(any(), any()) }
        }

    @Test fun `photo failure preserves text and allows later enrichment without overwriting saved fields`() =
        runBlocking {
            every { images.downloadProviderImage(any()) } throws IllegalStateException("offline")
            service.enrich(setOf(1))
            val result = slot<AuthorEnrichmentDto>()
            verify { profiles.saveAutomatically(1, capture(result)) }
            assertNull(result.captured.photoUrl)
            assertNull(result.captured.photoSourceUrl)
            assertEquals("Biografia", result.captured.biography)
            every { profiles.find(1) } returns result.captured.copy(biography = "Texto já salvo", location = "Origem existente")
            every { images.downloadProviderImage(any()) } returns ImageContent(byteArrayOf(1), null)
            service.enrich(setOf(1))
            verify {
                profiles.saveAutomatically(
                    1,
                    match {
                        it.biography == "Texto já salvo" &&
                            it.location == "Origem existente" &&
                            it.photoUrl == "/covers/portrait.png"
                    },
                )
            }
        }

    @Test fun `provider failure for one author does not interrupt following authors`() =
        runBlocking {
            every { candidates.uniqueIdentity(1) } returns 11L
            coEvery { api.fetchAuthorProfile(11, null) } throws IllegalStateException("offline")
            service.enrich(setOf(1, 2))
            verify(exactly = 0) { profiles.saveAutomatically(1, any()) }
            verify(exactly = 1) { profiles.saveAutomatically(2, any()) }
        }

    @Test fun `manual confirmation during provider call wins over automatic application`() =
        runBlocking {
            every { profiles.isManual(1) } returnsMany listOf(false, true)
            service.enrich(setOf(1))
            verify(exactly = 0) { profiles.saveAutomatically(any(), any()) }
        }

    @Test fun `new ambiguity discovered before commit prevents automatic save`() =
        runBlocking {
            every { candidates.uniqueIdentity(1) } returnsMany listOf(42L, null)
            service.enrich(setOf(1))
            verify(exactly = 0) { profiles.saveAutomatically(any(), any()) }
        }

    @Test fun `different existing identity is never replaced automatically`() =
        runBlocking {
            every { profiles.find(1) } returns AuthorEnrichmentDto(99, "Outra", "https://hardcover.app/authors/outra", Instant.EPOCH)
            service.enrich(setOf(1))
            coVerify(exactly = 0) { api.fetchAuthorProfile(any(), any()) }
        }
}
