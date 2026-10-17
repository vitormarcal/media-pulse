package dev.marcal.mediapulse.server.service.books

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.marcal.mediapulse.server.config.HardcoverProperties
import dev.marcal.mediapulse.server.integration.hardcover.HardcoverApiClient
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverBook
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverUserBook
import dev.marcal.mediapulse.server.model.EventSource
import dev.marcal.mediapulse.server.repository.hardcover.HardcoverNativeRepository
import dev.marcal.mediapulse.server.service.eventsource.EventSourceService
import dev.marcal.mediapulse.server.service.eventsource.ProcessEventSourceService
import dev.marcal.mediapulse.server.service.hardcover.HardcoverImportService
import dev.marcal.mediapulse.server.util.FingerprintUtil
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HardcoverBookIdentityTest {
    @Test
    fun `unchanged successful import backfills book identity without replaying reading`() =
        runBlocking {
            val api = mockk<HardcoverApiClient>()
            val events = mockk<EventSourceService>()
            val processor = mockk<ProcessEventSourceService>()
            val repository = mockk<HardcoverNativeRepository>(relaxed = true)
            val event = mockk<EventSource>()
            every { event.status } returns EventSource.Status.SUCCESS
            every { events.save(any(), any()) } returns event
            coEvery { api.fetchUserBooksPage(1, any(), 0) } returns
                listOf(HardcoverUserBook(id = 1, book = HardcoverBook(id = 20, title = "Existing")))
            coEvery { api.fetchUserBooksPage(1, any(), 1) } returns emptyList()
            val service =
                HardcoverImportService(
                    HardcoverProperties(enabled = true, token = "test", userId = 1),
                    api,
                    events,
                    processor,
                    jacksonObjectMapper(),
                    repository,
                )
            assertEquals(0, service.importUserBooks())
            verify { repository.retainHardcoverBookIdByFingerprint(FingerprintUtil.bookFp("Existing"), 20) }
            coVerify(exactly = 0) { processor.execute(any()) }
        }
}
