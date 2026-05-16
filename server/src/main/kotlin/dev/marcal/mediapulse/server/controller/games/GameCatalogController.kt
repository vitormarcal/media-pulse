package dev.marcal.mediapulse.server.controller.games

import dev.marcal.mediapulse.server.api.games.GameCatalogSuggestionsResponse
import dev.marcal.mediapulse.server.api.games.ManualGameCatalogCreateRequest
import dev.marcal.mediapulse.server.api.games.ManualGameCatalogCreateResponse
import dev.marcal.mediapulse.server.service.game.ManualGameCatalogCreateFlowService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/games/catalog")
class GameCatalogController(
    private val manualGameCatalogCreateFlowService: ManualGameCatalogCreateFlowService,
) {
    @GetMapping("/suggestions")
    fun suggestions(
        @RequestParam q: String,
    ): GameCatalogSuggestionsResponse = manualGameCatalogCreateFlowService.suggest(q)

    @PostMapping
    fun create(
        @RequestBody request: ManualGameCatalogCreateRequest,
    ): ManualGameCatalogCreateResponse = manualGameCatalogCreateFlowService.execute(request)
}
