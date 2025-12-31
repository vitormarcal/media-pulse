package dev.marcal.mediapulse.server.integration.spotify

import dev.marcal.mediapulse.server.model.image.ImageContent
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class SpotifyImageClient(
    private val spotifyImageWebClient: WebClient,
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    suspend fun downloadImage(url: String): ImageContent {
        logger.info("Downloading image: $url")
        return spotifyImageWebClient
            .get()
            .uri(url)
            .accept(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.ALL)
            .exchangeToMono { resp ->
                val ct = resp.headers().contentType().orElse(null)
                logger.info("Image: $url has been downloaded: $ct")
                resp
                    .bodyToMono<ByteArray>()
                    .map { bytes -> ImageContent(bytes = bytes, contentType = ct) }
            }.awaitSingle()
    }
}
