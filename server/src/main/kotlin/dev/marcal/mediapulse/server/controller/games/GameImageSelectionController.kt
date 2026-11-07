package dev.marcal.mediapulse.server.controller.games

import dev.marcal.mediapulse.server.api.games.GameImageSelectionRequest
import dev.marcal.mediapulse.server.service.game.GameImageSelectionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/games/{gameId}/images")
class GameImageSelectionController(
    private val service: GameImageSelectionService,
) {
    @GetMapping("/candidates")
    fun candidates(
        @PathVariable gameId: Long,
    ) = service.candidates(gameId)

    @PostMapping("/selection")
    fun save(
        @PathVariable gameId: Long,
        @RequestBody request: GameImageSelectionRequest,
    ) = service.save(gameId, request)
}
