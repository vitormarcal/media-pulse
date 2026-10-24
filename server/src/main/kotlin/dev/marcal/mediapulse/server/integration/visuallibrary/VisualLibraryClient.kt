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
    private val client =
        builder
            .clone()
            .defaultHeaders { headers ->
                if (properties.token.isNotEmpty()) headers.setBearerAuth(properties.token)
            }.codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) }
            .build()
    val enabled: Boolean get() = properties.enabled
    val migrationEnabled: Boolean get() = properties.migrationEnabled

    private data class AssetResponse(
        val id: UUID,
    )

    fun upload(
        image: ImageContent,
        metadata: VisualLibraryUploadMetadata,
    ): String {
        check(enabled || properties.migrationEnabled)
        val tags =
            metadata.tags
                .filterNotNull()
                .map { it.trim().replace(Regex("(?U)\\s+"), " ") }
                .filter { it.isNotBlank() && it.length <= 48 }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .take((8 - metadata.movements.size).coerceAtLeast(0))
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
            if (error is WebClientResponseException) {
                logger.warn(
                    "Falha no upload para Visual Library: status={} entityType={} entityId={} mimeType={} bytes={} response={}",
                    error.statusCode.value(),
                    metadata.sourceReference.entityType,
                    metadata.sourceReference.entityId,
                    image.contentType,
                    image.bytes.size,
                    responseDiagnostic(error),
                )
            } else {
                val cause = generateSequence(error as Throwable) { it.cause }.take(12).last()
                logger.warn(
                    "Falha no upload para Visual Library: error={} cause={} entityType={} entityId={} mimeType={} bytes={}",
                    error.javaClass.simpleName,
                    cause.javaClass.simpleName,
                    metadata.sourceReference.entityType,
                    metadata.sourceReference.entityId,
                    image.contentType,
                    image.bytes.size,
                )
            }
            throw VisualLibraryUploadException(error)
        }
    }

    private fun responseDiagnostic(error: WebClientResponseException): String {
        // Only structured error fields: never log the raw response, headers, image or request URL.
        val body = runCatching { mapper.readTree(error.responseBodyAsByteArray) }.getOrNull()
        val fields = listOf("code", "error", "statusMessage", "message", "detail")
        val values =
            fields.flatMap { field ->
                val value = body?.get(field)
                when {
                    value == null || value.isNull -> emptyList()
                    value.isTextual -> listOf("$field=${value.asText()}")
                    value.isObject ->
                        listOf("code", "message").mapNotNull { key ->
                            value
                                .get(key)
                                ?.takeIf { it.isTextual }
                                ?.asText()
                                ?.let { "$field.$key=$it" }
                        }
                    else -> emptyList()
                }
            }
        if (values.isEmpty()) return "sem detalhe JSON reconhecido"
        return values
            .joinToString("; ")
            .let { diagnostic ->
                if (properties.token.isEmpty()) diagnostic else diagnostic.replace(properties.token, "[omitido]")
            }.replace(Regex("https?://[^\\s]+"), "[URL omitida]")
            .replace(Regex("(?i)bearer\\s+[^\\s,;]+"), "Bearer [omitido]")
            .replace(Regex("(?i)(token|password|secret|api[_-]?key)\\s*[:=]\\s*[^\\s,;]+"), "$1=[omitido]")
            .replace(Regex("[\\p{Cntrl}]"), " ")
            .take(512)
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
        if (metadata.sourceReference.entityType == "artwork") {
            body.part(
                "managedTags",
                mapper.writeValueAsString(
                    mapOf(
                        "movements" to mapOf("tags" to metadata.movements, "revision" to metadata.movementsRevision),
                    ),
                ),
            )
        }
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

    data class MovementSyncResponse(
        val preservedTags: List<String> = emptyList(),
        val legacyTagsPreserved: Boolean = false,
    )

    fun synchronizeMovements(
        assetId: UUID,
        artworkId: Long,
        movements: List<String>,
        revision: Long,
    ): MovementSyncResponse {
        if (properties.baseUrl.isBlank()) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Visual Library não configurado.")
        try {
            return client
                .put()
                .uri("${properties.baseUrl.trimEnd('/')}/api/assets/$assetId/references/tags")
                .bodyValue(
                    mapOf(
                        "application" to "media-pulse",
                        "entityType" to "artwork",
                        "entityId" to artworkId.toString(),
                        "group" to "movements",
                        "tags" to movements,
                        "revision" to revision,
                    ),
                ).retrieve()
                .bodyToMono(MovementSyncResponse::class.java)
                .block(Duration.ofSeconds(30))
                ?: error("Visual Library retornou resposta vazia.")
        } catch (error: WebClientResponseException) {
            val reason =
                if (error.statusCode.value() == 409) {
                    "O Visual Library recusou a classificação. Confira o limite de oito tags por imagem e 48 caracteres por tag."
                } else {
                    "Não foi possível atualizar os movimentos no Visual Library. Tente novamente."
                }
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, reason)
        }
    }

    fun synchronizeBookSeries(
        assetId: UUID,
        entityType: String,
        entityId: Long,
        series: List<String>,
        revision: Long,
    ): MovementSyncResponse {
        require(entityType in setOf("book", "book_edition"))
        if (properties.baseUrl.isBlank()) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Visual Library não configurado.")
        try {
            return client
                .put()
                .uri("${properties.baseUrl.trimEnd('/')}/api/assets/$assetId/references/tags")
                .bodyValue(
                    mapOf(
                        "application" to "media-pulse",
                        "entityType" to entityType,
                        "entityId" to entityId.toString(),
                        "group" to "book-series",
                        "tags" to series,
                        "revision" to revision,
                    ),
                ).retrieve()
                .bodyToMono(MovementSyncResponse::class.java)
                .block(Duration.ofSeconds(30))
                ?: error("Visual Library retornou resposta vazia.")
        } catch (error: WebClientResponseException) {
            val code =
                runCatching {
                    mapper
                        .readTree(error.responseBodyAsByteArray)
                        ?.path("data")
                        ?.path("code")
                        ?.asText()
                }.getOrNull()
            val reason =
                when {
                    error.statusCode.value() == 409 &&
                        code in
                        setOf(
                            "STALE_REVISION",
                            "REVISION_CONFLICT",
                        )
                    -> "O Visual Library já possui outra revisão das séries desta referência."
                    error.statusCode.value() == 409 || error.statusCode.value() == 400 ->
                        "Não foi possível aplicar as séries: confira o limite de oito tags por imagem e 48 caracteres por tag no Visual Library."
                    error.statusCode.value() == 404 ->
                        "A imagem ou sua referência não foi encontrada no Visual Library. Confira o vínculo da capa."
                    else -> "Não foi possível atualizar as tags de séries no Visual Library. Tente novamente."
                }
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, reason)
        }
    }

    companion object {
        fun assetId(url: String): UUID? =
            if (Regex("/api/image-assets/[0-9a-fA-F-]{36}/content").matches(url)) {
                runCatching { UUID.fromString(url.split('/')[3]) }.getOrNull()
            } else {
                null
            }
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
