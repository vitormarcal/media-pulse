package dev.marcal.mediapulse.server.controller.spotify

import dev.marcal.mediapulse.server.controller.spotify.dto.SpotifyStatusResponse
import dev.marcal.mediapulse.server.service.spotify.SpotifyStatusService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/api/spotify")
@RestController
class SpotifyStatusController(
    private val statusService: SpotifyStatusService,
) {
    @GetMapping("/status")
    fun status(): SpotifyStatusResponse = statusService.getStatus()
}
