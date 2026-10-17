package dev.marcal.mediapulse.server.integration.visuallibrary

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import dev.marcal.mediapulse.server.config.VisualLibraryProperties
import dev.marcal.mediapulse.server.controller.ImageAssetsController
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.model.visuallibrary.VisualLibraryUploadMetadata
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import java.net.InetSocketAddress
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VisualLibraryClientTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val mapper = jacksonObjectMapper()
    private val metadata = VisualLibraryUploadMetadata.forEntity("album", 123, "Legião Urbana", "Dois", "álbum", "rock brasileiro")
    private val bytes = byteArrayOf(0, 1, 2, 3, -1)
    private var responseStatus = 201
    private var responseBody = """{"id":"$id","mimeType":"image/png","sizeBytes":5,"source":"media-pulse",
        "hiddenFromGallery":false,"createdAt":"2026-10-12T12:00:00.000Z","tags":[],"sourceReferences":[]}"""

    private fun client(enabled: Boolean = true) =
        VisualLibraryClient(
            VisualLibraryProperties(enabled, "http://127.0.0.1:${server.address.port}"),
            WebClient.builder(),
            mapper,
        )

    @AfterEach
    fun close() {
        server.stop(0)
    }

    @Test
    fun `classification update sends all movements with a string entity id and numeric revision`() {
        server.createContext("/api/assets/$id/references/tags") { exchange ->
            assertEquals("PUT", exchange.requestMethod)
            val body = mapper.readTree(exchange.requestBody.readAllBytes())
            assertEquals("media-pulse", body["application"].asText())
            assertEquals("artwork", body["entityType"].asText())
            assertEquals("7", body["entityId"].asText())
            assertTrue(body["entityId"].isTextual)
            assertEquals(4, body["revision"].asInt())
            assertEquals(listOf("Art Nouveau", "Simbolismo"), body["tags"].map { it.asText() })
            val bytes = """{"preservedTags":[],"legacyTagsPreserved":false}""".toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(responseStatus, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        responseStatus = 200
        assertEquals(false, client(false).synchronizeMovements(id, 7, listOf("Art Nouveau", "Simbolismo"), 4).legacyTagsPreserved)
        responseStatus = 409
        assertEquals(
            503,
            assertFailsWith<ResponseStatusException> {
                client(false).synchronizeMovements(id, 7, listOf("Art Nouveau", "Simbolismo"), 4)
            }.statusCode.value(),
        )
    }

    @Test
    fun `tag suggestions are cleaned deduplicated and bounded without truncating names`() {
        var sentTags = emptyList<String>()
        server.createContext("/api/assets") { exchange ->
            val text = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            val json = text.substringAfter("name=\"tags\"").substringAfter("\r\n\r\n").substringBefore("\r\n--")
            sentTags = mapper.readTree(json).map { it.asText() }
            exchange.responseHeaders.set("Content-Type", "application/json")
            val body = responseBody.toByteArray()
            exchange.sendResponseHeaders(201, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        val suggestions =
            listOf(null, "", "  Legião   Urbana ", "LEGIÃO URBANA", "x".repeat(49), "nome\ncom aspas \"teste\"") +
                (1..10).map { "tag $it" }
        client().upload(ImageContent(bytes, MediaType.IMAGE_PNG), metadata.copy(tags = suggestions))
        assertEquals(listOf("Legião Urbana", "nome com aspas \"teste\"") + (1..6).map { "tag $it" }, sentTags)
    }

    @Test
    fun `rejected enrichment retries once without tags and retains the same source reference`() {
        val payloads = mutableListOf<String>()
        server.createContext("/api/assets") { exchange ->
            payloads.add(exchange.requestBody.readAllBytes().toString(Charsets.UTF_8))
            exchange.responseHeaders.set("Content-Type", "application/json")
            val body = responseBody.toByteArray()
            exchange.sendResponseHeaders(if (payloads.size % 2 == 1) 400 else responseStatus, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        assertEquals("/api/image-assets/$id/content", client().upload(ImageContent(bytes, MediaType.IMAGE_PNG), metadata))
        assertEquals(2, payloads.size)
        val retry = payloads.last()
        val tags = retry.substringAfter("name=\"tags\"").substringAfter("\r\n\r\n").substringBefore("\r\n--")
        val reference = retry.substringAfter("name=\"sourceReference\"").substringAfter("\r\n\r\n").substringBefore("\r\n--")
        assertEquals(mapper.readTree("[]"), mapper.readTree(tags))
        assertEquals(mapper.valueToTree(metadata.sourceReference), mapper.readTree(reference))
        responseStatus = 400
        assertFailsWith<VisualLibraryUploadException> { client().upload(ImageContent(bytes, MediaType.IMAGE_PNG), metadata) }
        assertEquals(4, payloads.size)
    }

    @Test
    fun `multipart upload accepts both creation and deduplication and stores a host independent reference`() {
        server.createContext("/api/assets") { exchange ->
            assertEquals("POST", exchange.requestMethod)
            val raw = exchange.requestBody.readAllBytes()
            val payload = raw.toString(Charsets.ISO_8859_1)
            val text = raw.toString(Charsets.UTF_8)
            val tagsJson = text.substringAfter("name=\"tags\"").substringAfter("\r\n\r\n").substringBefore("\r\n--")
            val referenceJson = text.substringAfter("name=\"sourceReference\"").substringAfter("\r\n\r\n").substringBefore("\r\n--")
            assertEquals(mapper.valueToTree(metadata.tags), mapper.readTree(tagsJson))
            assertEquals("album", mapper.readTree(referenceJson)["entityType"].asText())
            assertTrue(mapper.readTree(referenceJson)["entityId"].isTextual)
            assertEquals("123", mapper.readTree(referenceJson)["entityId"].asText())
            assertTrue(payload.contains("name=\"image\""))
            assertTrue(payload.contains("Content-Type: image/png"))
            assertTrue(payload.contains(bytes.toString(Charsets.ISO_8859_1)))
            assertTrue(payload.contains("name=\"source\""))
            assertTrue(payload.contains("media-pulse"))
            assertTrue(payload.contains("name=\"hiddenFromGallery\""))
            assertTrue(payload.contains("false"))
            exchange.responseHeaders.set("Content-Type", "application/json")
            val body = responseBody.toByteArray()
            exchange.sendResponseHeaders(responseStatus, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        val client = client()
        for (status in listOf(201, 200)) {
            responseStatus = status
            assertEquals("/api/image-assets/$id/content", client.upload(ImageContent(bytes, MediaType.IMAGE_PNG), metadata))
        }
        responseStatus = 500
        assertEquals(
            503,
            assertFailsWith<VisualLibraryUploadException> {
                client.upload(ImageContent(bytes, MediaType.IMAGE_PNG), metadata)
            }.statusCode.value(),
        )
        responseStatus = 201
        responseBody = """{"id":"invalid"}"""
        assertFailsWith<VisualLibraryUploadException> { client.upload(ImageContent(bytes, MediaType.IMAGE_PNG), metadata) }
    }

    @Test
    fun `HTTP rejection logs bounded sanitized diagnostic without retrying 415`() {
        val logger = LoggerFactory.getLogger(VisualLibraryClient::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        var calls = 0
        server.createContext("/api/assets") { exchange ->
            calls++
            exchange.requestBody.readAllBytes()
            exchange.responseHeaders.set("Content-Type", "application/json")
            val body =
                mapper.writeValueAsBytes(
                    mapOf(
                        "statusMessage" to "Unsupported or invalid image.",
                        "error" to mapOf("code" to "INVALID_IMAGE", "message" to "JPEG rejected"),
                        "detail" to "JPEG\ninvalid Bearer secret-value token=hidden https://example.org/?secret=hidden " + "x".repeat(1000),
                        "privatePayload" to "never-log-this",
                    ),
                )
            exchange.sendResponseHeaders(415, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        try {
            assertFailsWith<VisualLibraryUploadException> { client().upload(ImageContent(bytes, MediaType.IMAGE_JPEG), metadata) }
            assertEquals(1, calls)
            val log = appender.list.single { it.formattedMessage.contains("Falha no upload") }.formattedMessage
            assertTrue(log.contains("status=415"))
            assertTrue(log.contains("entityType=album entityId=123 mimeType=image/jpeg bytes=5"))
            assertTrue(log.contains("Unsupported or invalid image."))
            assertTrue(log.contains("error.code=INVALID_IMAGE"))
            assertTrue(log.contains("Bearer [omitido] token=[omitido] [URL omitida]"))
            assertTrue(log.substringAfter("response=").length <= 512)
            for (secret in listOf("secret-value", "hidden", "never-log-this", "https://", "\n")) assertTrue(!log.contains(secret))
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    @Test
    fun `content is readable with uploads disabled and proxy preserves cache validators`() {
        var requestEtag: String? = null
        var requestDate: String? = null
        responseStatus = 200
        server.createContext("/api/assets/$id/content") { exchange ->
            requestEtag = exchange.requestHeaders.getFirst("If-None-Match")
            requestDate = exchange.requestHeaders.getFirst("If-Modified-Since")
            exchange.responseHeaders.set("Content-Type", "image/png")
            exchange.responseHeaders.set("Cache-Control", "private, max-age=3600")
            exchange.responseHeaders.set("ETag", "\"asset\"")
            exchange.responseHeaders.set("Last-Modified", "Mon, 12 Oct 2026 12:00:00 GMT")
            exchange.sendResponseHeaders(responseStatus, if (responseStatus == 304) -1 else bytes.size.toLong())
            exchange.responseBody.use { if (responseStatus != 304) it.write(bytes) }
        }
        val client = client(false)
        val response = client.content(id, null, null)
        assertContentEquals(bytes, response.body)
        assertEquals("image/png", response.headers.contentType.toString())
        assertEquals("private, max-age=3600", response.headers.cacheControl)
        assertEquals("\"asset\"", response.headers.eTag)
        val mvc = MockMvcBuilders.standaloneSetup(ImageAssetsController(client)).build()
        responseStatus = 304
        mvc
            .perform(
                get(
                    "/api/image-assets/$id/content",
                ).header("If-None-Match", "\"asset\"").header("If-Modified-Since", "Mon, 12 Oct 2026 12:00:00 GMT"),
            ).andExpect(status().isNotModified)
        assertEquals("\"asset\"", requestEtag)
        assertEquals("Mon, 12 Oct 2026 12:00:00 GMT", requestDate)
        responseStatus = 404
        mvc.perform(get("/api/image-assets/$id/content")).andExpect(status().isNotFound)
        responseStatus = 500
        mvc.perform(get("/api/image-assets/$id/content")).andExpect(status().isServiceUnavailable)
        mvc.perform(get("/api/image-assets/invalid/content")).andExpect(status().isBadRequest)
    }

    @Test
    fun `migration permits uploads independently of new upload flag`() {
        server.createContext("/api/assets") { exchange ->
            exchange.requestBody.readAllBytes()
            val body = responseBody.toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(201, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        val migrating =
            VisualLibraryClient(
                VisualLibraryProperties(baseUrl = "http://127.0.0.1:${server.address.port}", migrationEnabled = true),
                WebClient.builder(),
                mapper,
            )
        assertEquals(false, migrating.enabled)
        assertEquals("/api/image-assets/$id/content", migrating.upload(ImageContent(bytes, MediaType.IMAGE_PNG), metadata))
        assertFailsWith<IllegalStateException> { client(false).upload(ImageContent(bytes, MediaType.IMAGE_PNG), metadata) }
    }

    @Test
    fun `migration configuration requires a base URL and bounded batch and interval`() {
        assertFailsWith<IllegalArgumentException> { VisualLibraryProperties(migrationEnabled = true) }
        for (size in listOf(0, 101)) {
            assertFailsWith<IllegalArgumentException> { VisualLibraryProperties(migrationBatchSize = size) }
        }
        assertFailsWith<IllegalArgumentException> { VisualLibraryProperties(migrationIntervalMs = 999) }
        assertEquals(false, VisualLibraryProperties().migrationEnabled)
    }

    @Test
    fun `enabled flag requires valid base URL and unconfigured reads report unavailable`() {
        assertFailsWith<IllegalArgumentException> { VisualLibraryProperties(true) }
        for (url in listOf("file:///tmp/image", "http://user:password@localhost", "http://localhost?token=abc")) {
            assertFailsWith<IllegalArgumentException> { VisualLibraryProperties(true, url) }
        }
        val client = VisualLibraryClient(VisualLibraryProperties(), WebClient.builder(), mapper)
        assertEquals(503, assertFailsWith<ResponseStatusException> { client.content(id, null, null) }.statusCode.value())
    }
}
