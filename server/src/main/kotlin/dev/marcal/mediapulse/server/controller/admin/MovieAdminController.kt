package dev.marcal.mediapulse.server.controller.admin

import dev.marcal.mediapulse.server.api.movies.MovieCollectionBackfillResponse
import dev.marcal.mediapulse.server.api.movies.MovieCollectionMembersResponse
import dev.marcal.mediapulse.server.api.movies.MovieCompaniesBatchSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieCompaniesSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieCompanyMembersResponse
import dev.marcal.mediapulse.server.api.movies.MovieCreditsBatchSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieCreditsSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieTermsBatchSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieTermsSyncResponse
import dev.marcal.mediapulse.server.service.movie.MovieCollectionBackfillService
import dev.marcal.mediapulse.server.service.movie.MovieCollectionMembersService
import dev.marcal.mediapulse.server.service.movie.MovieCompaniesService
import dev.marcal.mediapulse.server.service.movie.MovieCompanyMembersService
import dev.marcal.mediapulse.server.service.movie.MovieCreditsService
import dev.marcal.mediapulse.server.service.movie.MovieTermsService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import kotlin.math.min

@RestController
@RequestMapping("/api/admin/movies")
class MovieAdminController(
    private val termsService: MovieTermsService,
    private val companiesService: MovieCompaniesService,
    private val creditsService: MovieCreditsService,
    private val collectionBackfillService: MovieCollectionBackfillService,
    private val collectionMembersService: MovieCollectionMembersService,
    private val companyMembersService: MovieCompanyMembersService,
) {
    @PostMapping("/{movieId}/terms/sync-tmdb")
    fun syncTerms(
        @PathVariable movieId: Long,
    ): MovieTermsSyncResponse = termsService.syncFromTmdb(movieId)

    @PostMapping("/terms/sync-tmdb")
    fun syncAllTerms(
        @RequestParam(defaultValue = "100") limit: Int,
    ): MovieTermsBatchSyncResponse = termsService.syncAllFromTmdb(normalizeLimit(limit))

    @PostMapping("/{movieId}/companies/sync-tmdb")
    fun syncCompanies(
        @PathVariable movieId: Long,
    ): MovieCompaniesSyncResponse = companiesService.syncFromTmdb(movieId)

    @PostMapping("/companies/sync-tmdb")
    fun syncAllCompanies(
        @RequestParam(defaultValue = "100") limit: Int,
    ): MovieCompaniesBatchSyncResponse = companiesService.syncAllFromTmdb(normalizeLimit(limit))

    @PostMapping("/{movieId}/credits/sync-tmdb")
    fun syncCredits(
        @PathVariable movieId: Long,
    ): MovieCreditsSyncResponse = creditsService.syncFromTmdb(movieId)

    @PostMapping("/credits/sync-tmdb")
    fun syncAllCredits(
        @RequestParam(defaultValue = "100") limit: Int,
    ): MovieCreditsBatchSyncResponse = creditsService.syncAllFromTmdb(normalizeLimit(limit))

    @PostMapping("/collections/backfill")
    fun backfillCollections(
        @RequestParam(defaultValue = "50") limit: Int,
    ): MovieCollectionBackfillResponse = collectionBackfillService.backfill(limit)

    @PostMapping("/collections/{collectionId}/tmdb-members")
    fun refreshCollection(
        @PathVariable collectionId: Long,
    ): MovieCollectionMembersResponse = collectionMembersService.refreshAndGetMembers(collectionId)

    @PostMapping("/companies/{companyId}/tmdb-members")
    fun refreshCompany(
        @PathVariable companyId: Long,
    ): MovieCompanyMembersResponse = companyMembersService.refreshAndGetMembers(companyId)

    private fun normalizeLimit(value: Int): Int {
        if (value < 1) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "limit deve ser >= 1")
        return min(value, 1000)
    }
}
