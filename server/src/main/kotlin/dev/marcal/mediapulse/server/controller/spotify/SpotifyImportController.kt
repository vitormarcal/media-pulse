package dev.marcal.mediapulse.server.controller.spotify

import dev.marcal.mediapulse.server.controller.spotify.dto.SpotifyImportRequest
import dev.marcal.mediapulse.server.controller.spotify.dto.SpotifyImportResponse
import dev.marcal.mediapulse.server.service.spotify.SpotifyImportService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/spotify")
@RestController
class SpotifyImportController(
    private val importService: SpotifyImportService,
) {
    @PostMapping("/import")
    suspend fun import(
        @RequestBody request: SpotifyImportRequest?,
    ): ResponseEntity<SpotifyImportResponse> {
        val imported =
            importService.importRecentlyPlayed(
                resetCursor = request?.resetCursor ?: false,
                maxPages = request?.maxPages,
            )

        return ResponseEntity.ok(
            SpotifyImportResponse(
                imported = imported,
                resetCursor = request?.resetCursor ?: false,
            ),
        )
    }
}
