package dev.marcal.mediapulse.server.integration.spotify

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import kotlin.test.assertEquals

class SpotifyAlbumApiClientTest {
    @Test
    fun `album request preserves all artists and ignores unrelated full album fields`() =
        runBlocking {
            val auth = mockk<SpotifyAuthService>()
            coEvery { auth.getValidAccessToken() } returns "test-token"
            val web =
                WebClient
                    .builder()
                    .baseUrl("https://spotify.test/v1")
                    .exchangeFunction { request ->
                        assertEquals("/v1/albums/album-id", request.url().path)
                        assertEquals("Bearer test-token", request.headers().getFirst(HttpHeaders.AUTHORIZATION))
                        Mono.just(
                            ClientResponse
                                .create(HttpStatus.OK)
                                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                .body(
                                    """{"id":"album-id","name":"Album","album_type":"album","artists":[{"id":"a","name":"A"},{"id":"b","name":"B"}]}""",
                                ).build(),
                        )
                    }.build()
            val album = SpotifyApiClient(web, auth).getAlbum("album-id")
            assertEquals(listOf("a", "b"), album.artists!!.map { it.id })
        }
}
