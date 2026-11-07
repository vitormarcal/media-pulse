package dev.marcal.mediapulse.server.integration.igdb

import com.sun.net.httpserver.HttpServer
import dev.marcal.mediapulse.server.config.IgdbProperties
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IgdbImageOptionsTest {
    @Test
    fun `image query expands cover screenshots and artworks and parses dimensions`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var query = ""
        server.createContext("/token") { exchange ->
            val body = """{"access_token":"test","expires_in":3600}""".toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/games") { exchange ->
            query = exchange.requestBody.bufferedReader().readText()
            val body =
                """[{"id":10,"cover":{"image_id":"c","width":600,"height":900},"screenshots":[{"image_id":"s","width":1920,"height":1080}],"artworks":[{"image_id":"a"}]}]"""
                    .toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}"
            val props = IgdbProperties(clientId = "test", clientSecret = "test")
            val client = IgdbApiClient(props, WebClient.builder().baseUrl(url).build(), WebClient.builder().baseUrl(url).build())
            val result = client.fetchImageOptions("10")!!
            assertEquals(600, result.cover?.width)
            assertEquals("s", result.screenshots.single().imageId)
            assertEquals(1080, result.screenshots.single().height)
            assertEquals("a", result.artworks.single().imageId)
            assertTrue(query.contains("screenshots.image_id"))
            assertTrue(query.contains("artworks.image_id"))
            assertTrue(query.contains("where id = 10;"))
        } finally {
            server.stop(0)
        }
    }
}
