package dev.marcal.mediapulse.server.integration.spotify

import dev.marcal.mediapulse.server.config.SpotifyProperties
import kotlinx.coroutines.reactor.awaitSingle
import org.apache.commons.codec.binary.Base64
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.reactive.function.client.WebClient

@Service
class SpotifyOAuthTokenService(
    private val props: SpotifyProperties,
    private val spotifyAccountsWebClient: WebClient,
) {
    data class SpotifyTokenExchangeResponse(
        val access_token: String,
        val token_type: String?,
        val scope: String?,
        val expires_in: Int?,
        val refresh_token: String?,
    )

    suspend fun exchangeCodeForTokens(code: String): SpotifyTokenExchangeResponse {
        val basic = Base64.encodeBase64String("${props.clientId}:${props.clientSecret}".toByteArray())

        val form =
            LinkedMultiValueMap<String, String>().apply {
                add("grant_type", "authorization_code")
                add("code", code)
                add("redirect_uri", props.oauth.redirectUri)
            }

        return spotifyAccountsWebClient
            .post()
            .uri("/api/token")
            .header(HttpHeaders.AUTHORIZATION, "Basic $basic")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue(form)
            .retrieve()
            .bodyToMono(SpotifyTokenExchangeResponse::class.java)
            .awaitSingle()
    }
}
