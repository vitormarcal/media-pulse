package dev.marcal.mediapulse.server.service.spotify

import dev.marcal.mediapulse.server.service.dispatch.DispatchResult
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.test.assertEquals

class SpotifyExtendedFileDispatcherTest {
    @MockK lateinit var spotifyExtendedPlaybackService: SpotifyExtendedPlaybackService

    private lateinit var dispatcher: SpotifyExtendedFileDispatcher

    @TempDir lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        // Use real ObjectMapper with Kotlin module for JSON processing
        val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
        objectMapper.registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
        dispatcher =
            SpotifyExtendedFileDispatcher(
                objectMapper = objectMapper,
                spotifyExtendedPlaybackService = spotifyExtendedPlaybackService,
            )
    }

    @Test
    fun `should process uncompressed JSON file with streaming and chunking`() =
        runBlocking {
            // Create test JSON file
            val testFile = Files.createFile(tempDir.resolve("history.json"))
            val jsonContent =
                """[
                    {"ts":"2020-01-01T00:00:00Z","master_metadata_track_name":"Track1","master_metadata_album_artist_name":"Artist","master_metadata_album_album_name":"Album","spotify_track_uri":"spotify:track:id1"},
                    {"ts":"2020-01-01T00:01:00Z","master_metadata_track_name":"Track2","master_metadata_album_artist_name":"Artist","master_metadata_album_album_name":"Album","spotify_track_uri":"spotify:track:id2"},
                    {"ts":"2020-01-01T00:02:00Z","master_metadata_track_name":"Track3","master_metadata_album_artist_name":"Artist","master_metadata_album_album_name":"Album","spotify_track_uri":"spotify:track:id3"}
                ]"""
            Files.writeString(testFile, jsonContent)

            val payload =
                """{"path":"${testFile.toAbsolutePath()}","sha256":"abc123","compressed":false}"""

            every { spotifyExtendedPlaybackService.processChunk(any(), any()) } just runs

            val result = dispatcher.dispatch(payload, eventId = 123L)

            assertEquals(DispatchResult.SUCCESS, result)
            verify(exactly = 1) { spotifyExtendedPlaybackService.processChunk(any(), 123L) }
        }

    @Test
    fun `should process gzip compressed JSON file`() =
        runBlocking {
            val testFile = tempDir.resolve("history.json.gz").toFile()

            // Create GZIP compressed JSON file
            val jsonContent =
                """[
                    {"ts":"2020-01-01T00:00:00Z","master_metadata_track_name":"Track1","master_metadata_album_artist_name":"Artist","master_metadata_album_album_name":"Album","spotify_track_uri":"spotify:track:id1"}
                ]"""
            GZIPOutputStream(testFile.outputStream()).use { it.write(jsonContent.toByteArray()) }

            val payload =
                """{"path":"${testFile.absolutePath}","sha256":"abc123","compressed":true}"""

            every { spotifyExtendedPlaybackService.processChunk(any(), any()) } just runs

            val result = dispatcher.dispatch(payload, eventId = 123L)

            assertEquals(DispatchResult.SUCCESS, result)
            verify(exactly = 1) { spotifyExtendedPlaybackService.processChunk(any(), 123L) }
        }

    @Test
    fun `should chunk items at 300 item boundary`() =
        runBlocking {
            // Create test JSON file with 350 items
            val testFile = Files.createFile(tempDir.resolve("history.json"))
            val itemsJson =
                (1..350).joinToString(",") {
                    """{"ts":"2020-01-01T00:00:00Z","master_metadata_track_name":"Track$it","master_metadata_album_artist_name":"Artist","master_metadata_album_album_name":"Album","spotify_track_uri":"spotify:track:id$it"}"""
                }
            val jsonContent = "[$itemsJson]"
            Files.writeString(testFile, jsonContent)

            val payload =
                """{"path":"${testFile.toAbsolutePath()}","sha256":"abc123","compressed":false}"""

            every { spotifyExtendedPlaybackService.processChunk(any(), any()) } just runs

            val result = dispatcher.dispatch(payload, eventId = 123L)

            assertEquals(DispatchResult.SUCCESS, result)
            // Should be called twice: once at 300 items, once at end for remaining 50
            verify(exactly = 2) { spotifyExtendedPlaybackService.processChunk(any(), 123L) }
        }

    @Test
    fun `should pass eventId to playback service`() =
        runBlocking {
            val testFile = Files.createFile(tempDir.resolve("history.json"))
            val jsonContent =
                """[
                    {"ts":"2020-01-01T00:00:00Z","master_metadata_track_name":"Track1","master_metadata_album_artist_name":"Artist","master_metadata_album_album_name":"Album","spotify_track_uri":"spotify:track:id1"}
                ]"""
            Files.writeString(testFile, jsonContent)

            val payload =
                """{"path":"${testFile.toAbsolutePath()}","sha256":"abc123","compressed":false}"""

            every { spotifyExtendedPlaybackService.processChunk(any(), any()) } just runs

            val result = dispatcher.dispatch(payload, eventId = 999L)

            assertEquals(DispatchResult.SUCCESS, result)
            verify(exactly = 1) { spotifyExtendedPlaybackService.processChunk(any(), 999L) }
        }

    @Test
    fun `should return SUCCESS on successful processing`() =
        runBlocking {
            val testFile = Files.createFile(tempDir.resolve("history.json"))
            Files.writeString(testFile, "[]")

            val payload =
                """{"path":"${testFile.toAbsolutePath()}","sha256":"abc123","compressed":false}"""

            every { spotifyExtendedPlaybackService.processChunk(any(), any()) } just runs

            val result = dispatcher.dispatch(payload, eventId = null)

            assertEquals(DispatchResult.SUCCESS, result)
        }
}
