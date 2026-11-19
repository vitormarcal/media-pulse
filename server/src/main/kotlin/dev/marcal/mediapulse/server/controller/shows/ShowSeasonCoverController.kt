package dev.marcal.mediapulse.server.controller.shows

import dev.marcal.mediapulse.server.api.image.TmdbImageSelectionRequest
import dev.marcal.mediapulse.server.service.tv.ShowSeasonCoverService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/shows/{showId}/seasons/{seasonNumber}/cover")
class ShowSeasonCoverController(
    private val service: ShowSeasonCoverService,
) {
    @GetMapping("/candidates")
    fun candidates(
        @PathVariable showId: Long,
        @PathVariable seasonNumber: Int,
    ) = service.candidates(showId, seasonNumber)

    @PostMapping
    fun save(
        @PathVariable showId: Long,
        @PathVariable seasonNumber: Int,
        @RequestBody request: TmdbImageSelectionRequest,
    ) = service.save(showId, seasonNumber, request)

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(
        @PathVariable showId: Long,
        @PathVariable seasonNumber: Int,
    ) = service.remove(showId, seasonNumber)
}
