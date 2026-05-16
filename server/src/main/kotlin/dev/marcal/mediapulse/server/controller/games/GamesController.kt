package dev.marcal.mediapulse.server.controller.games

import dev.marcal.mediapulse.server.api.games.GameDetailsResponse
import dev.marcal.mediapulse.server.api.games.GameSessionCreateRequest
import dev.marcal.mediapulse.server.api.games.GameSessionCreateResponse
import dev.marcal.mediapulse.server.api.games.GamesLibraryResponse
import dev.marcal.mediapulse.server.api.games.GamesSearchResponse
import dev.marcal.mediapulse.server.api.games.GamesStatsResponse
import dev.marcal.mediapulse.server.repository.GameQueryRepository
import dev.marcal.mediapulse.server.service.game.GameSessionService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import kotlin.math.min

@RestController
@RequestMapping("/api/games")
class GamesController(
    private val repository: GameQueryRepository,
    private val gameSessionService: GameSessionService,
) {
    @GetMapping("/library")
    fun library(
        @RequestParam(defaultValue = "24") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): GamesLibraryResponse = repository.library(normalizeLimit(limit), cursor)

    @GetMapping("/slug/{slug}")
    fun detailsBySlug(
        @PathVariable slug: String,
    ): GameDetailsResponse = repository.getGameDetailsBySlug(slug)

    @GetMapping("/{gameId}")
    fun details(
        @PathVariable gameId: Long,
    ): GameDetailsResponse = repository.getGameDetails(gameId)

    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "40") limit: Int,
    ): GamesSearchResponse = repository.search(q, normalizeLimit(limit))

    @GetMapping("/stats")
    fun stats(): GamesStatsResponse = repository.stats()

    @PostMapping("/{gameId}/sessions")
    fun addSession(
        @PathVariable gameId: Long,
        @RequestBody request: GameSessionCreateRequest,
    ): GameSessionCreateResponse = gameSessionService.create(gameId, request)

    @DeleteMapping("/{gameId}/sessions/{sessionId}")
    fun deleteSession(
        @PathVariable gameId: Long,
        @PathVariable sessionId: Long,
    ) {
        gameSessionService.delete(gameId, sessionId)
    }

    private fun normalizeLimit(limit: Int): Int = min(limit.coerceAtLeast(1), 100)
}
