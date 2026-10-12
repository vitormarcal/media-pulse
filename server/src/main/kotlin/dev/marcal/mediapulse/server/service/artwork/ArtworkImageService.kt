package dev.marcal.mediapulse.server.service.artwork

import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.model.artwork.ArtworkImage
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.model.visuallibrary.VisualLibraryUploadMetadata
import dev.marcal.mediapulse.server.repository.crud.ArtworkImageRepository
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.util.UUID
import javax.imageio.ImageIO

@Service
class ArtworkImageService(
    private val works: ArtworkService,
    private val images: ArtworkImageRepository,
    private val storage: ImageStorageService,
    private val visualLibrary: VisualLibraryClient,
) {
    companion object {
        const val MAX_BYTES = 10 * 1024 * 1024
    }

    fun artworkMetadata(id: Long): VisualLibraryUploadMetadata {
        val work = works.work(id)
        val artist = work.artistId?.let(works::artist)
        return VisualLibraryUploadMetadata.forEntity(
            "artwork",
            work.id,
            "arte visual",
            work.title,
            artist?.name,
            work.type,
            work.movement,
            work.technique,
        )
    }

    fun artistMetadata(id: Long): VisualLibraryUploadMetadata {
        val artist = works.artist(id)
        return VisualLibraryUploadMetadata.forEntity("artwork_artist", artist.id, "artista", artist.name, artist.countryOfOrigin)
    }

    fun upload(
        photo: MultipartFile,
        metadata: VisualLibraryUploadMetadata,
    ): String {
        if (photo.isEmpty || photo.size > MAX_BYTES) bad("Escolha uma imagem de até 10 MB.")
        return save(photo.bytes, metadata)
    }

    fun download(
        value: String,
        metadata: VisualLibraryUploadMetadata,
    ): String {
        val uri =
            try {
                URI(value.trim())
            } catch (_: Exception) {
                bad("URL de imagem inválida.")
            }
        if (uri.scheme?.lowercase() !in setOf("http", "https") ||
            uri.host == null ||
            uri.userInfo != null
        ) {
            bad("Use uma URL HTTP ou HTTPS.")
        }
        val connection =
            try {
                val addresses = InetAddress.getAllByName(uri.host)
                if (addresses.any {
                        it.isAnyLocalAddress ||
                            it.isLoopbackAddress ||
                            it.isLinkLocalAddress ||
                            it.isSiteLocalAddress ||
                            it.isMulticastAddress ||
                            (it.address.size == 16 && (it.address[0].toInt() and 0xfe) == 0xfc)
                    }
                ) {
                    bad("Use uma URL pública de imagem.")
                }
                uri.toURL().openConnection() as HttpURLConnection
            } catch (error: ResponseStatusException) {
                throw error
            } catch (_: Exception) {
                bad("Não foi possível acessar a imagem.")
            }
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false
        return try {
            if (connection.responseCode !in 200..299) bad("Use o link direto da imagem, sem redirecionamentos.")
            if (connection.contentLengthLong > MAX_BYTES) bad("Escolha uma imagem de até 10 MB.")
            val bytes = connection.inputStream.use { it.readNBytes(MAX_BYTES + 1) }
            save(bytes, metadata)
        } catch (
            error: ResponseStatusException,
        ) {
            throw error
        } catch (_: Exception) {
            bad("Não foi possível baixar a imagem.")
        } finally {
            connection.disconnect()
        }
    }

    private fun save(
        bytes: ByteArray,
        metadata: VisualLibraryUploadMetadata,
    ): String {
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
        val image =
            ImageContent(
                bytes,
                when (format) {
                    "jpeg" -> MediaType.IMAGE_JPEG
                    "png" -> MediaType.IMAGE_PNG
                    else -> MediaType.parseMediaType("image/webp")
                },
            )
        return if (visualLibrary.enabled) {
            visualLibrary.upload(
                image,
                metadata,
            )
        } else {
            storage.saveImageForArtwork(image, UUID.randomUUID().toString())
        }
    }

    @Transactional
    fun attach(
        id: Long,
        url: String,
        sourceUrl: String?,
        caption: String?,
    ): ArtworkImage {
        works.work(id)
        if (caption != null && caption.length > 500) bad("Legenda deve ter até 500 caracteres.")
        return images.save(
            ArtworkImage(
                artworkId = id,
                url = url,
                sourceUrl = sourceUrl,
                caption =
                    caption?.trim()?.ifBlank {
                        null
                    },
                primary = images.findByArtworkIdOrderByIdAsc(id).isEmpty(),
            ),
        )
    }

    @Transactional
    fun primary(
        id: Long,
        imageId: Long,
    ) {
        val image = image(id, imageId)
        val previous = images.findByArtworkIdOrderByIdAsc(id).find { it.primary }
        previous?.let { images.saveAndFlush(it.copy(primary = false)) }
        images.save(image.copy(primary = true))
    }

    @Transactional
    fun remove(
        id: Long,
        imageId: Long,
    ) {
        val image = image(id, imageId)
        images.delete(image)
        images.flush()
        if (image.primary) images.findByArtworkIdOrderByIdAsc(id).firstOrNull()?.let { images.save(it.copy(primary = true)) }
    }

    private fun image(
        id: Long,
        imageId: Long,
    ): ArtworkImage {
        works.work(id)
        val image = images.findById(imageId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem não encontrada.") }
        if (image.artworkId != id) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem não encontrada.")
        return image
    }

    private fun bad(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}
