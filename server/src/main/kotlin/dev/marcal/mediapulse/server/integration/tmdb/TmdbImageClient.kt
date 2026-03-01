package dev.marcal.mediapulse.server.integration.tmdb

import dev.marcal.mediapulse.server.model.image.ImageContent
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class TmdbImageClient(
    private val tmdbImageWebClient: WebClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun downloadImage(url: String): ImageContent {
        logger.info("Downloading TMDb image: {}", url)

        return tmdbImageWebClient
            .get()
            .uri(url)
            .accept(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.ALL)
            .exchangeToMono { response ->
                val contentType = response.headers().contentType().orElse(null)
                response
                    .bodyToMono<ByteArray>()
                    .map { bytes -> ImageContent(bytes = bytes, contentType = contentType) }
            }.block() ?: error("TMDb image download failed: $url")
    }
}
