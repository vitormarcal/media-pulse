package dev.marcal.mediapulse.server.integration.wikimedia

import dev.marcal.mediapulse.server.model.image.ImageContent
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

@Component
class WikimediaApiClient(
    @Qualifier("wikimediaWebClient") private val client: WebClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
        private const val MAX_ERROR_BODY_CHARS = 2_000
        private val supportedTypes = setOf("image/jpeg", "image/png", "image/webp")
    }

    suspend fun primaryImageFile(wikidataId: String): String? {
        val uri =
            UriComponentsBuilder
                .fromUriString("https://www.wikidata.org/w/api.php")
                .queryParam("action", "wbgetclaims")
                .queryParam("entity", wikidataId)
                .queryParam("property", "P18")
                .queryParam("format", "json")
                .build()
                .toUriString()
        return client
            .get()
            .uri(uri)
            .exchangeToMono { response ->
                if (response.statusCode().is2xxSuccessful) {
                    response.bodyToMono<WikidataClaimsResponse>()
                } else {
                    response.bodyToMono<String>().defaultIfEmpty("").flatMap { body ->
                        val requestId = response.headers().header("x-request-id").firstOrNull()
                        val contentType = response.headers().contentType().orElse(null)
                        val summarizedBody = summarizeErrorBody(body)
                        logger.warn(
                            "Wikidata response error | GET {} | status={} | contentType={} | requestId={} | body={}",
                            uri,
                            response.statusCode().value(),
                            contentType,
                            requestId,
                            summarizedBody,
                        )
                        Mono.error(
                            WebClientResponseException.create(
                                response.statusCode().value(),
                                response.statusCode().toString(),
                                response.headers().asHttpHeaders(),
                                body.toByteArray(StandardCharsets.UTF_8),
                                StandardCharsets.UTF_8,
                            ),
                        )
                    }
                }
            }.awaitSingle()
            .claims["P18"]
            ?.firstOrNull()
            ?.mainsnak
            ?.datavalue
            ?.value
    }

    suspend fun imageMetadata(fileName: String): WikimediaImageMetadata? {
        val title = if (fileName.startsWith("File:")) fileName else "File:$fileName"
        val uri =
            UriComponentsBuilder
                .fromUriString("https://commons.wikimedia.org/w/api.php")
                .queryParam("action", "query")
                .queryParam("format", "json")
                .queryParam("prop", "imageinfo")
                .queryParam("titles", title)
                .queryParam("iiprop", "url|size|mime|thumbmime|extmetadata")
                .queryParam("iiurlwidth", "1200")
                .queryParam("iiextmetadatalanguage", "pt")
                .queryParam("iiextmetadatafilter", "Artist|Credit|License|LicenseShortName|LicenseUrl|UsageTerms")
                .build()
                .toUriString()
        val info =
            client
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono<CommonsQueryResponse>()
                .awaitSingle()
                .query
                ?.pages
                ?.values
                ?.firstOrNull()
                ?.imageinfo
                ?.firstOrNull() ?: return null
        val downloadUrl = info.thumburl ?: info.url ?: return null
        return WikimediaImageMetadata(
            fileName = fileName,
            downloadUrl = downloadUrl,
            originalUrl = info.url ?: downloadUrl,
            descriptionUrl = info.descriptionurl,
            mimeType = info.thumbmime ?: info.mime,
            author = metadata(info, "Artist") ?: metadata(info, "Credit"),
            license = metadata(info, "LicenseShortName") ?: metadata(info, "License") ?: metadata(info, "UsageTerms"),
            licenseUrl = metadata(info, "LicenseUrl"),
        )
    }

    suspend fun downloadImage(metadata: WikimediaImageMetadata): ImageContent {
        require(java.net.URI(metadata.downloadUrl).host == "upload.wikimedia.org") { "Unexpected Wikimedia image host" }
        val expectedType = metadata.mimeType?.substringBefore(';')?.lowercase()
        require(expectedType == null || expectedType in supportedTypes) { "Unsupported Wikimedia image type: $expectedType" }
        val response =
            client
                .get()
                .uri(metadata.downloadUrl)
                .accept(MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.valueOf("image/webp"))
                .exchangeToMono { response ->
                    if (!response.statusCode().is2xxSuccessful) error("Wikimedia image download failed: ${response.statusCode()}")
                    val contentLength = response.headers().contentLength().orElse(-1)
                    require(contentLength < 0 || contentLength <= MAX_IMAGE_BYTES) { "Wikimedia image is too large" }
                    val contentType = response.headers().contentType().orElse(null)
                    response.bodyToMono<ByteArray>().map { ImageContent(it, contentType) }
                }.awaitSingleOrNull() ?: error("Wikimedia image download returned no content")
        val actualType =
            response.contentType
                ?.toString()
                ?.substringBefore(';')
                ?.lowercase()
        require(actualType in supportedTypes) { "Unsupported Wikimedia response type: $actualType" }
        require(response.bytes.isNotEmpty() && response.bytes.size <= MAX_IMAGE_BYTES) { "Invalid Wikimedia image size" }
        return response
    }

    private fun metadata(
        info: CommonsImageInfo,
        key: String,
    ): String? =
        info.extmetadata[key]
            ?.value
            ?.let(::plainText)
            ?.takeIf(String::isNotBlank)

    private fun plainText(value: String): String =
        org.springframework.web.util.HtmlUtils
            .htmlUnescape(value.replace(Regex("<[^>]+>"), " "))
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun summarizeErrorBody(body: String): String =
        body
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex(" +"), " ")
            .trim()
            .take(MAX_ERROR_BODY_CHARS)
            .ifBlank { "<empty>" }
}
