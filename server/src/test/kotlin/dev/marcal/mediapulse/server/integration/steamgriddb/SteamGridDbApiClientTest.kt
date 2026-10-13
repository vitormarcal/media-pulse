package dev.marcal.mediapulse.server.integration.steamgriddb

import com.sun.net.httpserver.HttpServer
import dev.marcal.mediapulse.server.config.SteamGridDbProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.net.InetSocketAddress
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SteamGridDbApiClientTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val url = "http://127.0.0.1:${server.address.port}/image"
    private val config = SteamGridDbClientConfig(SteamGridDbProperties())
    private val client =
        SteamGridDbApiClient(
            SteamGridDbProperties(),
            config.steamGridDbWebClient(WebClient.builder()),
            config.steamGridDbImageWebClient(WebClient.builder()),
        )

    @AfterEach fun close() {
        server.stop(0)
    }

    private fun serve(
        bytes: ByteArray,
        status: Int = 200,
    ) {
        server.createContext("/image") { exchange ->
            exchange.responseHeaders.set("Content-Type", "image/png")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            try {
                exchange.responseBody.use { it.write(bytes) }
            } catch (_: java.io.IOException) {
                // The client closes the stream when the response exceeds its limit.
            } finally {
                exchange.close()
            }
        }
    }

    @Test
    fun `downloads larger than default 256 KiB preserve all bytes and content type`() {
        val bytes = ByteArray(512 * 1024) { (it % 251).toByte() }
        serve(bytes)
        val image = client.downloadImage(url)
        assertContentEquals(bytes, image.bytes)
        assertEquals(MediaType.IMAGE_PNG, image.contentType)
    }

    @Test
    fun `accepts image at ten MiB boundary`() {
        val bytes = ByteArray(10 * 1024 * 1024)
        serve(bytes)
        assertEquals(bytes.size, client.downloadImage(url).bytes.size)
    }

    @Test
    fun `rejects responses above ten MiB`() {
        serve(ByteArray(10 * 1024 * 1024 + 1))
        assertFailsWith<DataBufferLimitException> { client.downloadImage(url) }
    }

    @Test
    fun `HTTP errors do not become image bytes`() {
        serve("missing".toByteArray(), 404)
        assertEquals(404, assertFailsWith<WebClientResponseException> { client.downloadImage(url) }.statusCode.value())
    }
}
