package dev.marcal.mediapulse.server.controller.games

import dev.marcal.mediapulse.server.api.games.GameImageReferenceRequest
import dev.marcal.mediapulse.server.service.game.GameImageManagementService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/games/{gameId}/images")
class GameImageManagementController(
    private val service: GameImageManagementService,
) {
    @PostMapping("/primary")
    fun primary(
        @PathVariable gameId: Long,
        @RequestBody request: GameImageReferenceRequest,
    ) = service.update(gameId, request.url, false)

    @DeleteMapping
    fun remove(
        @PathVariable gameId: Long,
        @RequestBody request: GameImageReferenceRequest,
    ) = service.update(gameId, request.url, true)
}
