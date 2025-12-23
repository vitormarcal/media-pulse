package dev.marcal.mediapulse.server.integration.spotify

import dev.marcal.mediapulse.server.model.image.ImageContent
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class SpotifyImageClient(
    private val spotifyImageWebClient: WebClient,
) {
    suspend fun downloadImage(url: String): ImageContent {
        val resp =
            spotifyImageWebClient
                .get()
                .uri(url)
                .retrieve()
                .toEntity(ByteArray::class.java)
                .awaitSingle()

        return ImageContent(
            bytes = resp.body ?: ByteArray(0),
            contentType = resp.headers.contentType,
        )
    }
}
