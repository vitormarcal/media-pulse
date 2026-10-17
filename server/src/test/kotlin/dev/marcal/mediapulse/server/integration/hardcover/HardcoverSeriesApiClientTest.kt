package dev.marcal.mediapulse.server.integration.hardcover

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HardcoverSeriesApiClientTest {
    @Test
    fun `catalog query decodes nullable positions and deduplicates series`() =
        runBlocking {
            val client =
                client(
                    """{"data":{"book_series":[{"series":{"id":5,"name":"Series","book_series":[{"book_id":10,"position":0.5},{"book_id":20,"position":null}]}},{"series":{"id":5,"name":"Series","book_series":[]}}]}}""",
                )
            val series = client.fetchSeries(10).single()
            assertEquals(5, series.id)
            assertEquals(listOf(10L, 20L), series.books.map { it.bookId })
            assertEquals(0.5, series.books.first().position)
        }

    @Test
    fun `GraphQL partial errors are failures and empty suggestions are valid`() =
        runBlocking {
            assertFailsWith<IllegalStateException> {
                client(
                    """{"data":{"book_series":[]},"errors":[{"message":"unavailable"}]}""",
                ).fetchSeries(10)
            }
            assertEquals(emptyList(), client("""{"data":{"book_series":[]}}""").fetchSeries(10))
        }

    private fun client(json: String) =
        HardcoverApiClient(
            WebClient
                .builder()
                .baseUrl("https://hardcover.test/graphql")
                .exchangeFunction {
                    Mono.just(
                        ClientResponse
                            .create(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body(json)
                            .build(),
                    )
                }.build(),
        )
}
