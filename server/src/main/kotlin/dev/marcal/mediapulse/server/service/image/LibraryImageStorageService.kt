package dev.marcal.mediapulse.server.service.image

import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.model.visuallibrary.VisualLibraryUploadMetadata
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import javax.imageio.ImageIO

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
        val validated = validate(image.bytes)
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
            storage.saveImageForBook(validated, provider, bookId, fileHint, editionId)
        }
    }

    fun saveGame(
        image: ImageContent,
        gameId: Long,
        title: String,
        provider: String,
        fileHint: String,
    ): String {
        val validated = validate(image.bytes)
        return if (visualLibrary.enabled) {
            visualLibrary.upload(validated, VisualLibraryUploadMetadata.forEntity("game", gameId, "jogo", title))
        } else {
            storage.saveImageForGame(validated, provider, gameId, fileHint)
        }
    }

    private fun validate(bytes: ByteArray): ImageContent {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) bad("Escolha uma imagem de até 10 MB.")
        val format =
            try {
                ImageIO.createImageInputStream(bytes.inputStream()).use { input ->
                    val readers = ImageIO.getImageReaders(input)
                    require(readers.hasNext())
                    val reader = readers.next()
                    try {
                        reader.input = input
                        val format = reader.formatName.lowercase()
                        require(format in setOf("jpeg", "png", "webp"))
                        require(reader.getWidth(0).toLong() * reader.getHeight(0) <= 40_000_000)
                        requireNotNull(reader.read(0))
                        format
                    } finally {
                        reader.dispose()
                    }
                }
            } catch (_: Exception) {
                bad("Escolha uma imagem JPEG, PNG ou WebP válida, com até 40 megapixels.")
            }
        return ImageContent(
            bytes,
            when (format) {
                "jpeg" -> MediaType.IMAGE_JPEG
                "png" -> MediaType.IMAGE_PNG
                else -> MediaType.parseMediaType("image/webp")
            },
        )
    }

    private fun bad(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    companion object {
        const val MAX_BYTES = 10 * 1024 * 1024
    }
}
