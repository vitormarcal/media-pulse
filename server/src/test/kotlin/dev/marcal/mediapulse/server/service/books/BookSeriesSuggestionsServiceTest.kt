package dev.marcal.mediapulse.server.service.books

import dev.marcal.mediapulse.server.api.books.BookGroupMemberDto
import dev.marcal.mediapulse.server.config.HardcoverProperties
import dev.marcal.mediapulse.server.integration.hardcover.HardcoverApiClient
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverSeriesBook
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverSuggestedSeries
import dev.marcal.mediapulse.server.repository.BookSeriesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BookSeriesSuggestionsServiceTest {
    private val repo = mockk<BookSeriesRepository>()
    private val client = mockk<HardcoverApiClient>()
    private val service = BookSeriesSuggestionsService(repo, client, HardcoverProperties(enabled = true, token = "test"))

    @Test
    fun `preview only returns existing local books and never changes the archive`() =
        runBlocking {
            every { repo.bookExists(1) } returns true
            every { repo.hardcoverId(1) } returns 10
            coEvery { client.fetchSeries(10) } returns
                listOf(HardcoverSuggestedSeries(5, "Series", listOf(HardcoverSeriesBook(10), HardcoverSeriesBook(20))))
            every { repo.localBooks(10, 1) } returns listOf(BookGroupMemberDto(1, "Local", 1))
            every { repo.localBooks(20, 2) } returns emptyList()
            val result = service.preview(1).single()
            assertEquals(listOf(1L), result.books.map { it.id })
            verify(exactly = 0) {
                repo.markSource(any(), any())
                repo.add(any(), any(), any())
            }
        }

    @Test
    fun `missing link unavailable provider and GraphQL failure are explicit`() =
        runBlocking {
            every { repo.bookExists(1) } returns true
            every { repo.hardcoverId(1) } returns null
            assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> { service.preview(1) }.statusCode)
            coVerify(exactly = 0) { client.fetchSeries(any()) }
            every { repo.hardcoverId(1) } returns 10
            val disabled = BookSeriesSuggestionsService(repo, client, HardcoverProperties(token = ""))
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertFailsWith<ResponseStatusException> { disabled.preview(1) }.statusCode)
            coEvery { client.fetchSeries(10) } throws IllegalStateException("GraphQL failed")
            assertEquals(HttpStatus.BAD_GATEWAY, assertFailsWith<ResponseStatusException> { service.preview(1) }.statusCode)
        }
}
