package dev.marcal.mediapulse.server.controller.shows

import dev.marcal.mediapulse.server.api.shows.ShowPersonCreditDto
import dev.marcal.mediapulse.server.api.shows.ShowTmdbCreditCandidatesResponse
import dev.marcal.mediapulse.server.api.shows.ShowTmdbCreditImportRequest
import dev.marcal.mediapulse.server.service.tv.ShowCreditsService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/shows")
class ShowCreditsController(
    private val showCreditsService: ShowCreditsService,
) {
    @PostMapping("/{showId}/credits/tmdb-candidates")
    fun tmdbCandidates(
        @PathVariable showId: Long,
    ): ShowTmdbCreditCandidatesResponse = showCreditsService.fetchTmdbCandidates(showId)

    @PostMapping("/{showId}/credits/from-tmdb")
    fun importTmdbCredit(
        @PathVariable showId: Long,
        @RequestBody request: ShowTmdbCreditImportRequest,
    ): ShowPersonCreditDto = showCreditsService.importTmdbCredit(showId, request)

    @DeleteMapping("/{showId}/people/{personId}")
    fun removeCredit(
        @PathVariable showId: Long,
        @PathVariable personId: Long,
        @RequestParam category: String,
    ) = showCreditsService.removeCredit(showId, personId, category)
}
