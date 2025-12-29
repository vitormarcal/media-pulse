package dev.marcal.mediapulse.server.service.spotify

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.controller.spotify.dto.SpotifyExtendedFileEventPayload
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyExtendedHistoryItem
import dev.marcal.mediapulse.server.service.dispatch.DispatchResult
import dev.marcal.mediapulse.server.service.dispatch.EventDispatcher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream

@Component
class SpotifyExtendedFileDispatcher(
    private val objectMapper: ObjectMapper,
    private val spotifyExtendedPlaybackService: SpotifyExtendedPlaybackService,
) : EventDispatcher {
    override val provider: String = SpotifyExtendedImportService.PROVIDER

    private val logger = LoggerFactory.getLogger(javaClass)
    private val jsonFactory = JsonFactory()

    override suspend fun dispatch(
        payload: String,
        eventId: Long?,
    ): DispatchResult {
        val meta = objectMapper.readValue(payload, SpotifyExtendedFileEventPayload::class.java)

        val file = File(meta.path)
        if (!file.exists()) {
            throw IllegalStateException("Spotify extended file not found: ${meta.path}")
        }

        var count = 0
        val chunk = ArrayList<SpotifyExtendedHistoryItem>(300)
        openInputStream(file, meta.compressed).use { input ->
            val parser = jsonFactory.createParser(input)

            // Expecting: [ { ... }, { ... }, ... ]
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw IllegalStateException("Invalid Spotify extended history JSON: expected START_ARRAY")
            }

            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken != JsonToken.START_OBJECT) {
                    parser.skipChildren()
                    continue
                }

                val item = objectMapper.readValue(parser, SpotifyExtendedHistoryItem::class.java)
                chunk.add(item)
                count++

                if (chunk.size >= 300) {
                    spotifyExtendedPlaybackService.processChunk(chunk, eventId)
                    chunk.clear()
                }
            }
        }

        if (chunk.isNotEmpty()) {
            spotifyExtendedPlaybackService.processChunk(chunk, eventId)
        }

        logger.info("Spotify extended file processed | eventId={} itemsSeen={}", eventId, count)
        return DispatchResult.SUCCESS
    }

    private fun openInputStream(
        file: File,
        compressed: Boolean,
    ) = if (compressed || file.name.endsWith(".gz")) {
        GZIPInputStream(FileInputStream(file))
    } else {
        FileInputStream(file)
    }
}
