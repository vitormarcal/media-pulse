package dev.marcal.mediapulse.server.integration.steamgriddb

import dev.marcal.mediapulse.server.config.SteamGridDbProperties
import dev.marcal.mediapulse.server.model.image.ImageContent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
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
    val thumb: String? = null,
)

private data class SteamGridDbEnvelope<T>(
    val success: Boolean = false,
    val data: T? = null,
)

@Component
class SteamGridDbApiClient(
    private val props: SteamGridDbProperties,
    @Qualifier("steamGridDbWebClient") private val steamGridDbWebClient: WebClient,
    @Qualifier("steamGridDbImageWebClient") private val steamGridDbImageWebClient: WebClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun searchGames(
        query: String,
        failOnError: Boolean = false,
    ): List<SteamGridDbGameResponse> {
        val normalized = query.trim()
        if (!props.enabled || props.apiKey.isBlank() || normalized.isBlank()) return emptyList()
        return getEnvelope<Array<SteamGridDbGameResponse>>("/search/autocomplete/{term}", normalized, failOnError = failOnError)
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

    fun fetchImageOptions(
        gameId: Long,
        heroes: Boolean = false,
    ): List<SteamGridDbGridResponse> {
        if (!props.enabled || props.apiKey.isBlank()) return emptyList()
        val category = if (heroes) "heroes" else "grids"
        val result =
            steamGridDbWebClient
                .get()
                .uri("/$category/game/{gameId}", gameId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${props.apiKey}")
                .retrieve()
                .bodyToMono<SteamGridDbEnvelope<Array<SteamGridDbGridResponse>>>()
                .block()
        check(result?.success == true) { "SteamGridDB indisponível" }
        return result?.data.orEmpty().toList()
    }

    fun downloadImage(url: String): ImageContent =
        steamGridDbImageWebClient
            .get()
            .uri(url)
            .accept(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.ALL)
            .exchangeToMono { response ->
                if (response.statusCode().isError) {
                    response.createException().flatMap {
                        reactor.core.publisher.Mono
                            .error<ImageContent>(it)
                    }
                } else {
                    val contentType = response.headers().contentType().orElse(null)
                    response.bodyToMono<ByteArray>().map { bytes -> ImageContent(bytes = bytes, contentType = contentType) }
                }
            }.block() ?: error("SteamGridDB image download failed: $url")

    private inline fun <reified T> getEnvelope(
        uri: String,
        vararg vars: Any,
        failOnError: Boolean = false,
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
            if (failOnError) throw error
            if (error !is WebClientResponseException) {
                logger.warn("SteamGridDB request failed", error)
            } else {
                logger.warn("SteamGridDB request failed with status {}", error.statusCode)
            }
        }.getOrNull()
}
