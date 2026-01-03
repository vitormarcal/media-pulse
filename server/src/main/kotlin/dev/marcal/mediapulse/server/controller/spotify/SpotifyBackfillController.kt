package dev.marcal.mediapulse.server.controller.spotify

import dev.marcal.mediapulse.server.service.spotify.SpotifyAlbumTracklistBackfillService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.core.task.TaskExecutor
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/spotify")
@RestController
class SpotifyBackfillController(
    private val service: SpotifyAlbumTracklistBackfillService,
    private val backgroundExecutor: TaskExecutor,
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    data class BackfillResponse(
        val limit: Int,
        val accepted: Boolean,
    )

    @PostMapping("/backfill-album-tracks")
    fun backfillAlbumTracks(
        @RequestParam(required = false, defaultValue = "50") limit: Int,
    ): ResponseEntity<BackfillResponse> {
        backgroundExecutor.execute {
            runBlocking {
                val res = service.backfillTop(limit = limit)
                // log only; endpoint returns 202 immediately
                // You can also persist a small event_source if you want audit.
                logger.info("Spotify album tracks backfill done | result={}", res)
            }
        }

        return ResponseEntity.accepted().body(
            BackfillResponse(
                limit = limit,
                accepted = true,
            ),
        )
    }
}
