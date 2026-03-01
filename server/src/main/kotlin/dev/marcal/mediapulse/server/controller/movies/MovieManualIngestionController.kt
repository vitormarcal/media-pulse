package dev.marcal.mediapulse.server.controller.movies

import dev.marcal.mediapulse.server.api.movies.ManualMovieWatchIngestRequest
import dev.marcal.mediapulse.server.api.movies.ManualMovieWatchIngestResponse
import dev.marcal.mediapulse.server.service.movie.ManualMovieWatchIngestionService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/movies")
class MovieManualIngestionController(
    private val manualMovieWatchIngestionService: ManualMovieWatchIngestionService,
) {
    @PostMapping("/watches")
    fun ingestManualWatches(
        @RequestBody request: ManualMovieWatchIngestRequest,
    ): ManualMovieWatchIngestResponse {
        require(request.items.isNotEmpty()) { "items deve ter pelo menos um item" }
        return manualMovieWatchIngestionService.ingest(request)
    }
}
