package dev.marcal.mediapulse.server.service.image

import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.model.visuallibrary.VisualLibraryUploadMetadata
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.util.UUID

@Service
class LibraryImageStorageService(
    private val storage: ImageStorageService,
    private val visualLibrary: VisualLibraryClient,
    @Qualifier("imagesWebClientBuilder") builder: WebClient.Builder,
) {
    private val client = builder.clone().codecs { it.defaultCodecs().maxInMemorySize(MAX_BYTES) }.build()

    fun downloadProviderImage(url: String): ImageContent {
        val response =
            client
                .get()
                .uri(url)
                .retrieve()
                .toEntity(ByteArray::class.java)
                .block(Duration.ofSeconds(30))
                ?: error("Provedor retornou imagem vazia.")
        return ImageContent(response.body ?: byteArrayOf(), response.headers.contentType)
    }

    fun saveBook(
        image: ImageContent,
        bookId: Long,
        title: String?,
        provider: String,
        editionId: Long? = null,
        fileHint: String? = title,
    ): String {
        val validated = LibraryImageValidator.validate(image.bytes)
        return if (visualLibrary.enabled) {
            visualLibrary.upload(
                validated,
                VisualLibraryUploadMetadata.forEntity(
                    if (editionId == null) "book" else "book_edition",
                    editionId ?: bookId,
                    "livro",
                    title,
                ),
            )
        } else {
            val localHint = if (visualLibrary.migrationEnabled) "${fileHint.orEmpty()}_${UUID.randomUUID()}" else fileHint
            storage.saveImageForBook(validated, provider, bookId, localHint, editionId)
        }
    }

    fun saveGame(
        image: ImageContent,
        gameId: Long,
        title: String,
        provider: String,
        fileHint: String,
    ): String {
        val validated = LibraryImageValidator.validate(image.bytes)
        return if (visualLibrary.enabled) {
            visualLibrary.upload(validated, VisualLibraryUploadMetadata.forEntity("game", gameId, "jogo", title))
        } else {
            // A pending local upload must not reuse a file that migration may delete
            // before its gallery link is committed.
            val localHint = if (visualLibrary.migrationEnabled) "${fileHint}_${UUID.randomUUID()}" else fileHint
            storage.saveImageForGame(validated, provider, gameId, localHint)
        }
    }

    companion object {
        const val MAX_BYTES = 10 * 1024 * 1024
    }
}
