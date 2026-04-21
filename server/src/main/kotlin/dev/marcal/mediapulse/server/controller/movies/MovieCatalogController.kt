package dev.marcal.mediapulse.server.controller.movies

import dev.marcal.mediapulse.server.api.movies.ManualMovieCatalogCreateRequest
import dev.marcal.mediapulse.server.api.movies.ManualMovieCatalogCreateResponse
import dev.marcal.mediapulse.server.api.movies.MovieCatalogSuggestionsResponse
import dev.marcal.mediapulse.server.api.movies.MovieCollectionBackfillResponse
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentApplyRequest
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentApplyResponse
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentPreviewRequest
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentPreviewResponse
import dev.marcal.mediapulse.server.service.movie.ManualMovieCatalogCreateFlowService
import dev.marcal.mediapulse.server.service.movie.MovieCollectionBackfillService
import dev.marcal.mediapulse.server.service.movie.MovieMetadataEnrichmentService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/movies")
class MovieCatalogController(
    private val manualMovieCatalogCreateFlowService: ManualMovieCatalogCreateFlowService,
    private val movieMetadataEnrichmentService: MovieMetadataEnrichmentService,
    private val movieCollectionBackfillService: MovieCollectionBackfillService,
) {
    @GetMapping("/catalog/suggestions")
    fun suggestCatalogEntry(
        @RequestParam q: String,
    ): MovieCatalogSuggestionsResponse = manualMovieCatalogCreateFlowService.suggest(q)

    @PostMapping("/catalog")
    fun createCatalogEntry(
        @RequestBody request: ManualMovieCatalogCreateRequest,
    ): ManualMovieCatalogCreateResponse = manualMovieCatalogCreateFlowService.execute(request)

    @PostMapping("/{movieId}/enrichment/preview")
    fun previewEnrichment(
        @PathVariable movieId: Long,
        @RequestBody request: MovieEnrichmentPreviewRequest,
    ): MovieEnrichmentPreviewResponse = movieMetadataEnrichmentService.preview(movieId, request)

    @PostMapping("/{movieId}/enrichment/apply")
    fun applyEnrichment(
        @PathVariable movieId: Long,
        @RequestBody request: MovieEnrichmentApplyRequest,
    ): MovieEnrichmentApplyResponse = movieMetadataEnrichmentService.apply(movieId, request)

    @PostMapping("/collections/backfill")
    fun backfillCollections(
        @RequestParam(defaultValue = "50") limit: Int,
    ): MovieCollectionBackfillResponse = movieCollectionBackfillService.backfill(limit)
}
