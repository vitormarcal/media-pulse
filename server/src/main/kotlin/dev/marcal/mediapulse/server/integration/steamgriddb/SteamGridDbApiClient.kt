package dev.marcal.mediapulse.server.integration.steamgriddb

import dev.marcal.mediapulse.server.config.SteamGridDbProperties
import dev.marcal.mediapulse.server.model.image.ImageContent
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono

data class SteamGridDbGameResponse(
    val id: Long,
    val name: String,
)

data class SteamGridDbGridResponse(
    val id: Long,
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    val style: String? = null,
    val score: Double? = null,
)

private data class SteamGridDbEnvelope<T>(
    val success: Boolean = false,
    val data: T? = null,
)

@Component
class SteamGridDbApiClient(
    private val props: SteamGridDbProperties,
    private val steamGridDbWebClient: WebClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun searchGames(query: String): List<SteamGridDbGameResponse> {
        val normalized = query.trim()
        if (!props.enabled || props.apiKey.isBlank() || normalized.isBlank()) return emptyList()
        return getEnvelope<Array<SteamGridDbGameResponse>>("/search/autocomplete/{term}", normalized)
            ?.data
            ?.toList()
            .orEmpty()
    }

    fun fetchGrids(gameId: Long): List<SteamGridDbGridResponse> {
        if (!props.enabled || props.apiKey.isBlank()) return emptyList()
        return getEnvelope<Array<SteamGridDbGridResponse>>(
            "/grids/game/{gameId}?dimensions=600x900,342x482,660x930",
            gameId,
        )?.data
            ?.toList()
            .orEmpty()
    }

    fun downloadImage(url: String): ImageContent =
        steamGridDbWebClient
            .get()
            .uri(url)
            .accept(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.ALL)
            .exchangeToMono { response ->
                val contentType = response.headers().contentType().orElse(null)
                response.bodyToMono<ByteArray>().map { bytes -> ImageContent(bytes = bytes, contentType = contentType) }
            }.block() ?: error("SteamGridDB image download failed: $url")

    private inline fun <reified T> getEnvelope(
        uri: String,
        vararg vars: Any,
    ): SteamGridDbEnvelope<T>? =
        runCatching {
            steamGridDbWebClient
                .get()
                .uri(uri, *vars)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${props.apiKey}")
                .retrieve()
                .bodyToMono<SteamGridDbEnvelope<T>>()
                .block()
        }.onFailure { error ->
            if (error !is WebClientResponseException) {
                logger.warn("SteamGridDB request failed", error)
            } else {
                logger.warn("SteamGridDB request failed with status {}", error.statusCode)
            }
        }.getOrNull()
}
