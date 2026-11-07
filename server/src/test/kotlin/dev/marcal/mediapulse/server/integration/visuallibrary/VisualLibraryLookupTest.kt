package dev.marcal.mediapulse.server.integration.visuallibrary

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sun.net.httpserver.HttpServer
import dev.marcal.mediapulse.server.config.VisualLibraryProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import java.net.InetSocketAddress
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VisualLibraryLookupTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val mapper = jacksonObjectMapper()
    private val hash = "a".repeat(64)
    private val absent = "b".repeat(64)
    private val asset = UUID.randomUUID()
    private val token = "synthetic-token"
    private val client =
        VisualLibraryClient(
            VisualLibraryProperties(true, "http://127.0.0.1:${server.address.port}", token = token),
            WebClient.builder(),
            mapper,
        )

    @AfterEach fun close() {
        server.stop(0)
    }

    @Test
    fun `lookup sends authenticated hashes and maps exact matches absent entries and repetitions`() {
        server.createContext("/api/assets/lookup") { exchange ->
            assertEquals("POST", exchange.requestMethod)
            assertEquals("Bearer $token", exchange.requestHeaders.getFirst("Authorization"))
            assertEquals("application/json", exchange.requestHeaders.getFirst("Content-Type"))
            val body = mapper.readTree(exchange.requestBody.readAllBytes())
            assertEquals(listOf(hash, absent, hash), body["hashes"].map { it.asText() })
            val result =
                mapper.writeValueAsBytes(
                    mapOf(
                        "results" to
                            listOf(
                                mapOf("sha256" to hash, "exists" to true, "asset" to mapOf("id" to asset.toString())),
                                mapOf("sha256" to absent, "exists" to false, "asset" to null),
                                mapOf("sha256" to hash, "exists" to true, "asset" to mapOf("id" to asset.toString())),
                            ),
                    ),
                )
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, result.size.toLong())
            exchange.responseBody.use { it.write(result) }
        }
        assertEquals(mapOf(hash to asset, absent to null), client.lookupHashes(listOf(hash, absent, hash)))
    }

    @Test
    fun `invalid or unsupported remote responses never become missing assets`() {
        var status = 404
        var body = "{}"
        server.createContext("/api/assets/lookup") { exchange ->
            exchange.requestBody.readAllBytes()
            val result = body.toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, result.size.toLong())
            exchange.responseBody.use { it.write(result) }
        }
        assertEquals(503, assertFailsWith<ResponseStatusException> { client.lookupHashes(listOf(hash)) }.statusCode.value())
        status = 401
        assertEquals(503, assertFailsWith<ResponseStatusException> { client.lookupHashes(listOf(hash)) }.statusCode.value())
        status = 200
        for (invalid in listOf(
            """{"results":[]}""",
            """{"results":[{"sha256":"$hash","asset":null}]}""",
            """{"results":[{"sha256":"$hash","exists":true,"asset":null}]}""",
            """{"results":[{"sha256":"$absent","exists":false,"asset":null}]}""",
        )) {
            body = invalid
            assertEquals(503, assertFailsWith<ResponseStatusException> { client.lookupHashes(listOf(hash)) }.statusCode.value())
        }
        assertFailsWith<IllegalArgumentException> { client.lookupHashes(emptyList()) }
        assertFailsWith<IllegalArgumentException> { client.lookupHashes(List(101) { hash }) }
    }
}
