package dev.marcal.mediapulse.server.controller.movies

import dev.marcal.mediapulse.server.api.movies.ManualMovieWatchCreateRequest
import dev.marcal.mediapulse.server.api.movies.ManualMovieWatchCreateResponse
import dev.marcal.mediapulse.server.service.movie.ManualMovieWatchCreateFlowService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/movies")
class ManualMovieWatchController(
    private val manualMovieWatchCreateFlowService: ManualMovieWatchCreateFlowService,
) {
    @PostMapping("/watches")
    fun createManualWatch(
        @RequestBody request: ManualMovieWatchCreateRequest,
    ): ManualMovieWatchCreateResponse = manualMovieWatchCreateFlowService.execute(request)
}
