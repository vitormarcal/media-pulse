package dev.marcal.mediapulse.server.controller.oauth

import dev.marcal.mediapulse.server.config.SpotifyProperties
import dev.marcal.mediapulse.server.service.spotify.SpotifyAuthorizationService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference

@RequestMapping("/oauth/spotify")
@RestController
class SpotifyOAuthController(
    private val props: SpotifyProperties,
    private val authorizationService: SpotifyAuthorizationService,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(SpotifyOAuthController::class.java)
    }

    private val random = SecureRandom()
    private val lastState = AtomicReference<String?>(null)

    @GetMapping("/login")
    fun login(): ResponseEntity<Void> {
        if (!props.oauth.enabled) return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

        val state = generateState()
        lastState.set(state)

        val scopesEnc = url(props.oauth.scopes)
        val redirectEnc = url(props.oauth.redirectUri)

        val authorizeUrl =
            "${props.accountsBaseUrl.trimEnd('/')}/authorize" +
                "?client_id=${url(props.clientId)}" +
                "&response_type=code" +
                "&redirect_uri=$redirectEnc" +
                "&scope=$scopesEnc" +
                "&state=${url(state)}"

        logger.info("Spotify OAuth login redirecting to authorize URL")
        return ResponseEntity
            .status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, authorizeUrl)
            .build()
    }

    @GetMapping("/callback")
    suspend fun callback(
        @RequestParam("code", required = false) code: String?,
        @RequestParam("state", required = false) state: String?,
        @RequestParam("error", required = false) error: String?,
    ): ResponseEntity<String> {
        if (!props.oauth.enabled) return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Spotify OAuth disabled")

        if (!error.isNullOrBlank()) {
            return ResponseEntity.badRequest().body("Spotify OAuth error: $error")
        }

        if (code.isNullOrBlank()) {
            return ResponseEntity.badRequest().body("Missing 'code' in callback")
        }

        val expected = lastState.getAndSet(null)
        if (expected == null || state != expected) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid state")
        }

        if (!authorizationService.authorize(code)) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                    "No refresh_token returned. This usually happens if you already authorized and are reusing the same consent. " +
                        "Try removing access in your Spotify account apps and authorize again.",
                )
        }

        logger.info("Spotify OAuth success: credentials stored")
        return ResponseEntity
            .status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, "/")
            .build()
    }

    private fun generateState(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun url(v: String): String = URLEncoder.encode(v, StandardCharsets.UTF_8)
}
