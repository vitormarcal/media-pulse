package dev.marcal.mediapulse.server.integration.spotify

import dev.marcal.mediapulse.server.config.SpotifyProperties
import kotlinx.coroutines.reactor.awaitSingle
import org.apache.commons.codec.binary.Base64
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant
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

    suspend fun getValidAccessToken(): String {
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
                .retrieve()
                .bodyToMono(TokenResponse::class.java)
                .awaitSingle()

        val expiresAt = Instant.now().plusSeconds((resp.expires_in ?: 3600).toLong())
        return CachedToken(accessToken = resp.access_token, expiresAt = expiresAt)
    }

    data class TokenResponse(
        val access_token: String,
        val token_type: String? = null,
        val scope: String? = null,
        val expires_in: Int? = null,
    )
}
