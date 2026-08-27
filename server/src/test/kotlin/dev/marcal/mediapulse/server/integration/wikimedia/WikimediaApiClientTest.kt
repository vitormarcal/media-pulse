package dev.marcal.mediapulse.server.integration.wikimedia

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WikimediaApiClientTest {
    @Test
    fun `reads the primary image claim from a successful response`() =
        runBlocking {
            val client =
                clientReturning(
                    HttpStatus.OK,
                    """{"entities":{"Q2831":{"claims":{"P18":[{"mainsnak":{"datavalue":{"value":"Michael Jackson 1984.jpg"}}}]}}}}""",
                )

            assertEquals("Michael Jackson 1984.jpg", client.primaryImageFile("Q2831"))
        }

    @Test
    fun `preserves response details when Wikidata rejects the request`() =
        runBlocking {
            val client = clientReturning(HttpStatus.BAD_REQUEST, "Invalid request from edge", "request-123")

            val error = assertFailsWith<WebClientResponseException.BadRequest> { client.primaryImageFile("Q2831") }

            assertEquals("Invalid request from edge", error.responseBodyAsString)
            assertEquals("request-123", error.headers.getFirst("x-request-id"))
        }

    private fun clientReturning(
        status: HttpStatus,
        body: String,
        requestId: String? = null,
    ): WikimediaApiClient {
        val webClient =
            WebClient
                .builder()
                .exchangeFunction {
                    val response =
                        ClientResponse
                            .create(status)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    requestId?.let { response.header("x-request-id", it) }
                    Mono.just(response.body(body).build())
                }.build()
        return WikimediaApiClient(webClient)
    }
}
