package dev.marcal.mediapulse.server.integration.spotify

import dev.marcal.mediapulse.server.config.SpotifyProperties
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.apache.commons.codec.binary.Base64
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Service
class SpotifyAuthService(
    private val props: SpotifyProperties,
    private val spotifyAccountsWebClient: WebClient,
) {
    data class CachedToken(
        val accessToken: String,
        val expiresAt: Instant,
    )

    private val cache = AtomicReference<CachedToken?>(null)
    private val reauthorizationRequired = AtomicBoolean(false)

    suspend fun getValidAccessToken(): String {
        if (reauthorizationRequired.get()) {
            throw SpotifyReauthorizationRequiredException()
        }
        val now = Instant.now()
        val existing = cache.get()
        if (existing != null && now.isBefore(existing.expiresAt.minusSeconds(30))) {
            return existing.accessToken
        }
        val fresh = refresh()
        cache.set(fresh)
        return fresh.accessToken
    }

    private suspend fun refresh(): CachedToken {
        val basic = Base64.encodeBase64String("${props.clientId}:${props.clientSecret}".toByteArray())

        val form =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "refresh_token")
                add("refresh_token", props.refreshToken)
            }

        val resp =
            spotifyAccountsWebClient
                .post()
                .uri("/api/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic $basic")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .exchangeToMono { response ->
                    if (response.statusCode().is2xxSuccessful) {
                        response.bodyToMono(TokenResponse::class.java)
                    } else {
                        response
                            .bodyToMono(TokenErrorResponse::class.java)
                            .defaultIfEmpty(TokenErrorResponse(error = "http_${response.statusCode().value()}"))
                            .flatMap { error ->
                                val exception =
                                    if (error.error == "invalid_grant") {
                                        reauthorizationRequired.set(true)
                                        SpotifyReauthorizationRequiredException(error.error_description)
                                    } else {
                                        SpotifyTokenRefreshException(
                                            errorCode = error.error,
                                            providerDescription = error.error_description,
                                            message = "Spotify token refresh failed (${error.error})",
                                        )
                                    }
                                reactor.core.publisher.Mono
                                    .error(exception)
                            }
                    }
                }.awaitSingleOrNull()
                ?: throw SpotifyTokenRefreshException("empty_response", null, "Spotify token response was empty")

        val expiresAt = Instant.now().plusSeconds((resp.expires_in ?: 3600).toLong())
        return CachedToken(accessToken = resp.access_token, expiresAt = expiresAt)
    }

    data class TokenResponse(
        val access_token: String,
        val token_type: String? = null,
        val scope: String? = null,
        val expires_in: Int? = null,
    )

    data class TokenErrorResponse(
        val error: String,
        val error_description: String? = null,
    )
}
