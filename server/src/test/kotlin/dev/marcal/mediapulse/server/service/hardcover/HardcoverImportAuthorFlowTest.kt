package dev.marcal.mediapulse.server.service.hardcover

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.marcal.mediapulse.server.config.HardcoverProperties
import dev.marcal.mediapulse.server.integration.hardcover.HardcoverApiClient
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverUserBook
import dev.marcal.mediapulse.server.model.EventSource
import dev.marcal.mediapulse.server.repository.hardcover.HardcoverNativeRepository
import dev.marcal.mediapulse.server.service.eventsource.EventSourceService
import dev.marcal.mediapulse.server.service.eventsource.ProcessEventSourceService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HardcoverImportAuthorFlowTest {
    private val api = mockk<HardcoverApiClient>()
    private val events = mockk<EventSourceService>()
    private val processor = mockk<ProcessEventSourceService>()
    private val native = mockk<HardcoverNativeRepository>(relaxed = true)
    private val authors = mockk<HardcoverAuthorImportService>(relaxed = true)
    private val event = mockk<EventSource>()
    private val service =
        HardcoverImportService(
            HardcoverProperties(enabled = true, token = "test", userId = 1),
            api,
            events,
            processor,
            jacksonObjectMapper(),
            native,
            authors,
        )

    init {
        every { event.status } returns EventSource.Status.SUCCESS
        every { events.save(any(), any()) } returns event
        val items = listOf(HardcoverUserBook(1), HardcoverUserBook(2))
        coEvery { api.fetchUserBooksPage(1, any(), 0) } returns items
        coEvery { api.fetchUserBooksPage(1, any(), 2) } returns emptyList()
        every { authors.observe(any()) } returns setOf(28)
    }

    @Test fun `unchanged books still contribute evidence and enrich once after full catalog`() =
        runBlocking {
            assertEquals(0, service.importUserBooks())
            verify(exactly = 2) { authors.observe(any()) }
            coVerify(exactly = 1) { authors.enrich(setOf(28)) }
            coVerify(exactly = 0) { processor.execute(any()) }
        }

    @Test fun `partial catalog records evidence without assuming identity is unambiguous`() =
        runBlocking {
            service.importUserBooks(maxPages = 1)
            verify(exactly = 2) { authors.observe(any()) }
            coVerify(exactly = 0) { authors.enrich(any()) }
        }

    @Test fun `evidence failure prevents unsafe enrichment and does not fail book import`() =
        runBlocking {
            every { authors.observe(any()) } throws IllegalStateException("offline")
            assertEquals(0, service.importUserBooks())
            coVerify(exactly = 0) { authors.enrich(any()) }
        }

    @Test fun `profile failure does not change successful book import result`() =
        runBlocking {
            coEvery { authors.enrich(any()) } throws IllegalStateException("offline")
            assertEquals(0, service.importUserBooks())
        }
}
