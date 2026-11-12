package dev.marcal.mediapulse.server.integration.hardcover

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class HardcoverAuthorApiClientTest {
    @Test
    fun `author context accepts nullable dates links and photo by id or slug`() =
        runBlocking {
            val client =
                client(
                    """{"data":{"authors":[{"id":42,"name":"Pessoa","slug":"pessoa","bio":"Texto",
            "born_date":null,"born_year":1980,"death_year":null,"location":"Brasil",
            "links":{"website":"https://example.org"},"image":{"url":"https://images.hardcover.app/photo.jpg"}}]}}""",
                )
            for (result in listOf(client.fetchAuthorProfile(42, null)!!, client.fetchAuthorProfile(null, "pessoa")!!)) {
                assertEquals(42L, result.id)
                assertEquals(1980, result.bornYear)
                assertNull(result.bornDate)
                assertEquals("Texto", result.bio)
                assertEquals("https://example.org", result.links?.get("website")?.asText())
                assertEquals("https://images.hardcover.app/photo.jpg", result.image?.url)
            }
        }

    @Test
    fun `empty result and graphql partial errors are distinct`() =
        runBlocking {
            assertNull(client("""{"data":{"authors":[]}}""").fetchAuthorProfile(42, null))
            assertFailsWith<IllegalStateException> {
                client("""{"data":{"authors":[]},"errors":[{"message":"unavailable"}]}""").fetchAuthorProfile(42, null)
            }
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
