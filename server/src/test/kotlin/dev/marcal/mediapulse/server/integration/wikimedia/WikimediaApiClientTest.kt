package dev.marcal.mediapulse.server.integration.wikimedia

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikimediaApiClientTest {
    @Test
    fun `reads the primary image claim from a successful response`() =
        runBlocking {
            var request: ClientRequest? = null
            val client =
                clientReturning(
                    HttpStatus.OK,
                    """{"claims":{"P18":[{"mainsnak":{"datavalue":{"value":"Michael Jackson 1984.jpg"}}}]}}""",
                    onRequest = { request = it },
                )

            assertEquals("Michael Jackson 1984.jpg", client.primaryImageFile("Q2831"))
            assertEquals("/w/api.php", request?.url()?.path)
            assertEquals("wbgetclaims", request?.url()?.query?.queryValue("action"))
            assertEquals("Q2831", request?.url()?.query?.queryValue("entity"))
            assertEquals("P18", request?.url()?.query?.queryValue("property"))
            assertEquals("json", request?.url()?.query?.queryValue("format"))
        }

    @Test
    fun `preserves response details when Wikidata rejects the request`() =
        runBlocking {
            val client = clientReturning(HttpStatus.BAD_REQUEST, "Invalid request from edge", "request-123")

            val error = assertFailsWith<WebClientResponseException.BadRequest> { client.primaryImageFile("Q2831") }

            assertEquals("Invalid request from edge", error.responseBodyAsString)
            assertEquals("request-123", error.headers.getFirst("x-request-id"))
        }

    @Test
    fun `does not propagate authorization inherited from a shared builder`() =
        runBlocking {
            var request: ClientRequest? = null
            val builder =
                WebClient
                    .builder()
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer internal-token")
                    .exchangeFunction {
                        request = it
                        Mono.just(
                            ClientResponse
                                .create(HttpStatus.OK)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .body("""{"claims":{}}""")
                                .build(),
                        )
                    }
            val client = WikimediaApiClient(WikimediaClientConfig().wikimediaWebClient(builder))

            client.primaryImageFile("Q2831")

            assertNull(request?.headers()?.getFirst(HttpHeaders.AUTHORIZATION))
        }

    @Test
    fun `downloads an already encoded Wikimedia URL without encoding it again`() =
        runBlocking {
            var request: ClientRequest? = null
            val webClient =
                WebClient
                    .builder()
                    .exchangeFunction {
                        request = it
                        Mono.just(
                            ClientResponse
                                .create(HttpStatus.OK)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE)
                                .body(byteArrayOf(1, 2, 3).toString(Charsets.ISO_8859_1))
                                .build(),
                        )
                    }.build()
            val metadata =
                WikimediaImageMetadata(
                    fileName = "Portrait (contrast).jpg",
                    downloadUrl = "https://upload.wikimedia.org/Portrait_%28contrast%29.jpg",
                    originalUrl = "https://upload.wikimedia.org/Portrait_%28contrast%29.jpg",
                    descriptionUrl = null,
                    mimeType = MediaType.IMAGE_JPEG_VALUE,
                    author = null,
                    license = null,
                    licenseUrl = null,
                )

            val image = WikimediaApiClient(webClient).downloadImage(metadata)

            assertEquals("/Portrait_%28contrast%29.jpg", request?.url()?.rawPath)
            assertTrue(image.bytes.isNotEmpty())
        }

    @ParameterizedTest
    @ValueSource(strings = ["upload.wikimedia.org", "thumb.wikimedia.org"])
    fun `downloads images from the explicit Wikimedia hosts`(host: String) =
        runBlocking {
            var request: ClientRequest? = null
            val webClient =
                WebClient
                    .builder()
                    .exchangeFunction {
                        request = it
                        Mono.just(
                            ClientResponse
                                .create(HttpStatus.OK)
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE)
                                .body("image bytes")
                                .build(),
                        )
                    }.build()
            val url = "https://$host/wikipedia/commons/thumb/portrait_%C3%B6.jpg?utm_source=commons.wikimedia.org"

            WikimediaApiClient(webClient).downloadImage(imageMetadata(url))

            assertEquals(url, request?.url()?.toASCIIString())
        }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://example.org/image.jpg",
            "https://thumb.wikimedia.org.example.org/image.jpg",
            "https://upload.wikimedia.org@example.org/image.jpg",
            "http://thumb.wikimedia.org/image.jpg",
            "https://thumb.wikimedia.org:8443/image.jpg",
            "https://user@thumb.wikimedia.org/image.jpg",
        ],
    )
    fun `rejects unexpected image URLs before making a request`(url: String) =
        runBlocking {
            var requested = false
            val client = clientReturning(HttpStatus.OK, "", onRequest = { requested = true })

            assertFailsWith<IllegalArgumentException> { client.downloadImage(imageMetadata(url)) }

            assertEquals(false, requested)
        }

    private fun imageMetadata(url: String) =
        WikimediaImageMetadata(
            fileName = "portrait.jpg",
            downloadUrl = url,
            originalUrl = url,
            descriptionUrl = null,
            mimeType = MediaType.IMAGE_JPEG_VALUE,
            author = null,
            license = null,
            licenseUrl = null,
        )

    private fun clientReturning(
        status: HttpStatus,
        body: String,
        requestId: String? = null,
        onRequest: (ClientRequest) -> Unit = {},
    ): WikimediaApiClient {
        val webClient =
            WebClient
                .builder()
                .exchangeFunction { request ->
                    onRequest(request)
                    val response =
                        ClientResponse
                            .create(status)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    requestId?.let { response.header("x-request-id", it) }
                    Mono.just(response.body(body).build())
                }.build()
        return WikimediaApiClient(webClient)
    }

    private fun String.queryValue(name: String): String? =
        split('&')
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.firstOrNull() == name }
            ?.getOrNull(1)
}
