package dev.marcal.mediapulse.server.controller.spotify

import dev.marcal.mediapulse.server.controller.spotify.dto.SpotifyImportRequest
import dev.marcal.mediapulse.server.controller.spotify.dto.SpotifyImportResponse
import dev.marcal.mediapulse.server.service.spotify.SpotifyImportService
import kotlinx.coroutines.runBlocking
import org.springframework.core.task.TaskExecutor
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/spotify")
@RestController
class SpotifyImportController(
    private val importService: SpotifyImportService,
    private val backgroundExecutor: TaskExecutor,
) {
    @PostMapping("/import")
    fun import(
        @RequestBody request: SpotifyImportRequest?,
    ): ResponseEntity<SpotifyImportResponse> {
        val resetCursor = request?.resetCursor ?: false
        val maxPages = request?.maxPages

        backgroundExecutor.execute {
            runBlocking {
                importService.importRecentlyPlayed(
                    resetCursor = resetCursor,
                    maxPages = maxPages,
                )
            }
        }

        return ResponseEntity.accepted().body(
            SpotifyImportResponse(
                resetCursor = resetCursor,
            ),
        )
    }
}
