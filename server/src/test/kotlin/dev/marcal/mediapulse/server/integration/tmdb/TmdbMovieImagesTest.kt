package dev.marcal.mediapulse.server.integration.tmdb

import com.sun.net.httpserver.HttpServer
import dev.marcal.mediapulse.server.config.TmdbProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.web.reactive.function.client.WebClient
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class TmdbMovieImagesTest {
    @ParameterizedTest
    @ValueSource(strings = ["movie", "tv", "tv-season"])
    fun `fetches alternative posters and backdrops without a language filter`(kind: String) {
        val route = if (kind == "tv-season") "tv/42/season/0" else "$kind/42"
        val request = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/3/$route/images") { exchange ->
            request.set("${exchange.requestMethod} ${exchange.requestURI}")
            val body =
                """
                {
                  "id":42,
                  "posters":[{"file_path":"/one.jpg","width":780,"height":1170},{"file_path":"/two.jpg"}],
                  "backdrops":[{"file_path":"/background.jpg"}],
                  "logos":[{"file_path":"/logo.png"}]
                }
                """.trimIndent().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val properties = TmdbProperties(apiBaseUrl = "http://127.0.0.1:${server.address.port}/3", apiKey = "fixture")
            val client = TmdbApiClient(TmdbClientConfig(properties).tmdbWebClient(WebClient.builder()), properties, TmdbRateLimiter())
            val result =
                when (kind) {
                    "tv" -> client.fetchShowImages("42")
                    "tv-season" -> client.fetchShowSeasonImages("42", 0)
                    else -> client.fetchMovieImages("42")
                }
            assertEquals("GET /3/$route/images?api_key=fixture", request.get())
            assertEquals(listOf("/one.jpg", "/two.jpg"), result.posters.map { it.filePath })
            assertEquals(780, result.posters.first().width)
            assertEquals("/background.jpg", result.backdrops.single().filePath)
        } finally {
            server.stop(0)
        }
    }
}
