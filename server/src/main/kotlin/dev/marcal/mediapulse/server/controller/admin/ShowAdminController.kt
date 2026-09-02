package dev.marcal.mediapulse.server.controller.admin

import dev.marcal.mediapulse.server.api.shows.ShowCreditsBatchSyncResponse
import dev.marcal.mediapulse.server.api.shows.ShowCreditsSyncResponse
import dev.marcal.mediapulse.server.service.tv.ShowCreditsService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import kotlin.math.min

@RestController
@RequestMapping("/api/admin/shows")
class ShowAdminController(
    private val creditsService: ShowCreditsService,
) {
    @PostMapping("/{showId}/credits/sync-tmdb")
    fun syncCredits(
        @PathVariable showId: Long,
    ): ShowCreditsSyncResponse = creditsService.syncFromTmdb(showId)

    @PostMapping("/credits/sync-tmdb")
    fun syncAllCredits(
        @RequestParam(defaultValue = "100") limit: Int,
    ): ShowCreditsBatchSyncResponse = creditsService.syncAllFromTmdb(normalizeLimit(limit))

    private fun normalizeLimit(value: Int): Int {
        if (value < 1) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "limit deve ser >= 1")
        return min(value, 1000)
    }
}
