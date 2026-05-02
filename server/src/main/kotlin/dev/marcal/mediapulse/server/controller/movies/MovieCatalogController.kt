package dev.marcal.mediapulse.server.controller.movies

import dev.marcal.mediapulse.server.api.movies.ManualMovieCatalogCreateRequest
import dev.marcal.mediapulse.server.api.movies.ManualMovieCatalogCreateResponse
import dev.marcal.mediapulse.server.api.movies.MovieCatalogSuggestionsResponse
import dev.marcal.mediapulse.server.api.movies.MovieCollectionBackfillResponse
import dev.marcal.mediapulse.server.api.movies.MovieCollectionMembersResponse
import dev.marcal.mediapulse.server.api.movies.MovieCollectionSummaryDto
import dev.marcal.mediapulse.server.api.movies.MovieCompanyMembersResponse
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentApplyRequest
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentApplyResponse
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentPreviewRequest
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentPreviewResponse
import dev.marcal.mediapulse.server.api.movies.MoviePersonFilmographyResponse
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import dev.marcal.mediapulse.server.service.movie.ManualMovieCatalogCreateFlowService
import dev.marcal.mediapulse.server.service.movie.MovieCollectionBackfillService
import dev.marcal.mediapulse.server.service.movie.MovieCollectionMembersService
import dev.marcal.mediapulse.server.service.movie.MovieCompanyMembersService
import dev.marcal.mediapulse.server.service.movie.MovieMetadataEnrichmentService
import dev.marcal.mediapulse.server.service.movie.MoviePersonFilmographyService
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
    private val repository: MovieQueryRepository,
    private val manualMovieCatalogCreateFlowService: ManualMovieCatalogCreateFlowService,
    private val movieMetadataEnrichmentService: MovieMetadataEnrichmentService,
    private val movieCollectionBackfillService: MovieCollectionBackfillService,
    private val movieCollectionMembersService: MovieCollectionMembersService,
    private val movieCompanyMembersService: MovieCompanyMembersService,
    private val moviePersonFilmographyService: MoviePersonFilmographyService,
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

    @GetMapping("/collections/{collectionId}/tmdb-members")
    fun collectionTmdbMembers(
        @PathVariable collectionId: Long,
    ): MovieCollectionMembersResponse = movieCollectionMembersService.fetchMembers(collectionId)

    @GetMapping("/collections")
    fun collections(): List<MovieCollectionSummaryDto> = repository.listMovieCollections()

    @GetMapping("/companies/{companyId}/tmdb-members")
    fun companyTmdbMembers(
        @PathVariable companyId: Long,
    ): MovieCompanyMembersResponse = movieCompanyMembersService.fetchMembers(companyId)

    @GetMapping("/people/{personId}/tmdb-filmography")
    fun personTmdbFilmography(
        @PathVariable personId: Long,
    ): MoviePersonFilmographyResponse = moviePersonFilmographyService.fetchFilmography(personId)
}
