package dev.marcal.mediapulse.server.controller.shows

import dev.marcal.mediapulse.server.api.image.TmdbImageSelectionRequest
import dev.marcal.mediapulse.server.api.shows.ShowImageReferenceRequest
import dev.marcal.mediapulse.server.service.tv.ShowSeasonCoverService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/shows/{showId}/seasons/{seasonNumber}/images")
class ShowSeasonImageController(
    private val service: ShowSeasonCoverService,
) {
    @GetMapping("/candidates")
    fun candidates(
        @PathVariable showId: Long,
        @PathVariable seasonNumber: Int,
    ) = service.candidates(showId, seasonNumber)

    @PostMapping("/selection")
    fun select(
        @PathVariable showId: Long,
        @PathVariable seasonNumber: Int,
        @RequestBody request: TmdbImageSelectionRequest,
    ) = service.saveImages(showId, seasonNumber, request)

    @PostMapping("/lookup")
    fun lookup(
        @PathVariable showId: Long,
        @PathVariable seasonNumber: Int,
        @RequestBody request: TmdbImageSelectionRequest,
    ) = service.lookup(showId, seasonNumber, request)

    @PostMapping("/primary")
    fun primary(
        @PathVariable showId: Long,
        @PathVariable seasonNumber: Int,
        @RequestBody request: ShowImageReferenceRequest,
    ) = service.manage(showId, seasonNumber, request.url, false)

    @DeleteMapping
    fun remove(
        @PathVariable showId: Long,
        @PathVariable seasonNumber: Int,
        @RequestBody request: ShowImageReferenceRequest,
    ) = service.manage(showId, seasonNumber, request.url, true)
}
