package dev.marcal.mediapulse.server.integration.visuallibrary

import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.config.VisualLibraryProperties
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.model.visuallibrary.VisualLibraryUploadMetadata
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.util.Locale
import java.util.UUID

class VisualLibraryUploadException(
    cause: Exception,
) : ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Não foi possível enviar a imagem ao Visual Library. Tente novamente.", cause)

@Component
class VisualLibraryClient(
    private val properties: VisualLibraryProperties,
    @Qualifier("imagesWebClientBuilder") builder: WebClient.Builder,
    private val mapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val client = builder.clone().codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) }.build()
    val enabled: Boolean get() = properties.enabled

    private data class AssetResponse(
        val id: UUID,
    )

    fun upload(
        image: ImageContent,
        metadata: VisualLibraryUploadMetadata,
    ): String {
        check(enabled)
        val tags =
            metadata.tags
                .filterNotNull()
                .map { it.trim().replace(Regex("(?U)\\s+"), " ") }
                .filter { it.isNotBlank() && it.length <= 48 }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .take(8)
        try {
            return try {
                sendUpload(image, metadata, tags)
            } catch (error: WebClientResponseException) {
                if (error.statusCode.value() != 400 || tags.isEmpty()) throw error
                // Existing personal tags may already occupy the asset's eight slots.
                // Preserve those tags and retry once with only the entity reference.
                logger.info("Visual Library recusou enriquecimento; repetindo upload sem sugestões de tags.")
                sendUpload(image, metadata, emptyList())
            }
        } catch (error: Exception) {
            logger.warn("Falha no upload para Visual Library: {}", error.javaClass.simpleName)
            throw VisualLibraryUploadException(error)
        }
    }

    private fun sendUpload(
        image: ImageContent,
        metadata: VisualLibraryUploadMetadata,
        tags: List<String>,
    ): String {
        val body = MultipartBodyBuilder()
        body
            .part(
                "image",
                object : ByteArrayResource(image.bytes) {
                    override fun getFilename() = "image"
                },
            ).contentType(image.contentType ?: MediaType.APPLICATION_OCTET_STREAM)
        body.part("source", "media-pulse")
        body.part("hiddenFromGallery", "false")
        body.part("tags", mapper.writeValueAsString(tags))
        body.part("sourceReference", mapper.writeValueAsString(metadata.sourceReference))
        val asset =
            client
                .post()
                .uri("${properties.baseUrl.trimEnd('/')}/api/assets")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(body.build())
                .retrieve()
                .bodyToMono(AssetResponse::class.java)
                .block(Duration.ofSeconds(30))
                ?: error("Visual Library retornou resposta vazia.")
        return "/api/image-assets/${asset.id}/content"
    }

    // Existing references remain readable even when uploads are disabled.
    fun content(
        id: UUID,
        etag: String?,
        modifiedSince: String?,
    ): ResponseEntity<ByteArray> {
        if (properties.baseUrl.isBlank()) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Visual Library não configurado.")
        try {
            val response =
                client
                    .get()
                    .uri("${properties.baseUrl.trimEnd('/')}/api/assets/$id/content")
                    .headers { headers ->
                        etag?.let { headers.set(HttpHeaders.IF_NONE_MATCH, it) }
                        modifiedSince?.let { headers.set(HttpHeaders.IF_MODIFIED_SINCE, it) }
                    }.retrieve()
                    .toEntity(ByteArray::class.java)
                    .block(Duration.ofSeconds(30))
                    ?: error("Visual Library retornou resposta vazia.")
            val headers = HttpHeaders()
            for (name in listOf(HttpHeaders.CONTENT_TYPE, HttpHeaders.CACHE_CONTROL, HttpHeaders.ETAG, HttpHeaders.LAST_MODIFIED)) {
                response.headers[name]?.let { headers[name] = it }
            }
            headers.set("X-Content-Type-Options", "nosniff")
            return ResponseEntity(response.body, headers, response.statusCode)
        } catch (error: WebClientResponseException) {
            val status = if (error.statusCode.value() == 404) HttpStatus.NOT_FOUND else HttpStatus.SERVICE_UNAVAILABLE
            throw ResponseStatusException(status, "Imagem indisponível.")
        } catch (error: Exception) {
            logger.warn("Falha na leitura de asset {}: {}", id, error.javaClass.simpleName)
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Imagem indisponível.")
        }
    }
}
