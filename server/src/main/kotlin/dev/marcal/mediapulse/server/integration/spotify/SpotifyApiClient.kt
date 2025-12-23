package dev.marcal.mediapulse.server.integration.spotify

import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyRecentlyPlayedResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

@Component
class SpotifyApiClient(
    private val spotifyApiWebClient: WebClient,
    private val authService: SpotifyAuthService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun getRecentlyPlayed(
        afterMs: Long,
        limit: Int = 50,
    ): SpotifyRecentlyPlayedResponse {
        require(limit in 1..50)
        val accessToken = authService.getValidAccessToken()

        return executeWith429Retry {
            spotifyApiWebClient
                .get()
                .uri { builder ->
                    builder
                        .path("/me/player/recently-played")
                        .queryParam("after", afterMs)
                        .queryParam("limit", limit)
                        .build()
                }.header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .retrieve()
                .toEntity(object : ParameterizedTypeReference<SpotifyRecentlyPlayedResponse>() {})
                .awaitSingle()
                .body ?: SpotifyRecentlyPlayedResponse()
        }
    }

    private suspend fun <T> executeWith429Retry(
        maxRetries: Int = 3,
        block: suspend () -> T,
    ): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (ex: org.springframework.web.reactive.function.client.WebClientResponseException) {
                if (ex.statusCode == HttpStatus.TOO_MANY_REQUESTS && attempt < maxRetries) {
                    val retryAfterSeconds = ex.headers.getFirst("Retry-After")?.toLongOrNull()
                    val waitSec = retryAfterSeconds ?: 2L
                    logger.warn("Spotify rate limited (429). Waiting {}s then retrying... attempt={}", waitSec, attempt + 1)
                    delay(Duration.ofSeconds(waitSec).toMillis())
                    attempt++
                    continue
                }
                throw ex
            }
        }
    }
}
