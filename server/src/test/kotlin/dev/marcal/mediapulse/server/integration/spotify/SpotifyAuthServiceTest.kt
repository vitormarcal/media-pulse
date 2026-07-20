package dev.marcal.mediapulse.server.integration.spotify

import dev.marcal.mediapulse.server.config.SpotifyProperties
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpotifyAuthServiceTest {
    private val props =
        SpotifyProperties(
            clientId = "client-id",
            clientSecret = "client-secret",
            refreshToken = "refresh-token",
        )

    @Test
    fun `should cache successful access token`() =
        runBlocking {
            val calls = AtomicInteger()
            val service = serviceWith(calls, HttpStatus.OK, """{"access_token":"access-token","expires_in":3600}""")

            assertEquals("access-token", service.getValidAccessToken())
            assertEquals("access-token", service.getValidAccessToken())
            assertEquals(1, calls.get())
        }

    @Test
    fun `should block further provider calls after invalid grant`() =
        runBlocking {
            val calls = AtomicInteger()
            val service =
                serviceWith(
                    calls,
                    HttpStatus.BAD_REQUEST,
                    """{"error":"invalid_grant","error_description":"Refresh token expired"}""",
                )

            val first = assertFailsWith<SpotifyReauthorizationRequiredException> { service.getValidAccessToken() }
            assertEquals("Refresh token expired", first.providerDescription)
            assertFailsWith<SpotifyReauthorizationRequiredException> { service.getValidAccessToken() }
            assertEquals(1, calls.get())
        }

    @Test
    fun `should not block retries for other token errors`() =
        runBlocking {
            val calls = AtomicInteger()
            val service = serviceWith(calls, HttpStatus.BAD_REQUEST, """{"error":"invalid_client"}""")

            repeat(2) {
                val error = assertFailsWith<SpotifyTokenRefreshException> { service.getValidAccessToken() }
                assertEquals("invalid_client", error.errorCode)
            }
            assertEquals(2, calls.get())
        }

    private fun serviceWith(
        calls: AtomicInteger,
        status: HttpStatus,
        body: String,
    ): SpotifyAuthService {
        val webClient =
            WebClient
                .builder()
                .exchangeFunction {
                    calls.incrementAndGet()
                    Mono.just(
                        ClientResponse
                            .create(status)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body(body)
                            .build(),
                    )
                }.build()
        return SpotifyAuthService(props, webClient)
    }
}
