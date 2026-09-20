package dev.marcal.mediapulse.server.controller.shows

import dev.marcal.mediapulse.server.service.tv.ShowPersonalMarksService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/shows")
class ShowPersonalMarksController(
    private val service: ShowPersonalMarksService,
) {
    @PostMapping("/{showId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markFavorite(
        @PathVariable showId: Long,
    ) = service.setFavorite(showId, true)

    @DeleteMapping("/{showId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unmarkFavorite(
        @PathVariable showId: Long,
    ) = service.setFavorite(showId, false)

    @PostMapping("/{showId}/abandoned")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markAbandoned(
        @PathVariable showId: Long,
    ) = service.setAbandoned(showId, true)

    @DeleteMapping("/{showId}/abandoned")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unmarkAbandoned(
        @PathVariable showId: Long,
    ) = service.setAbandoned(showId, false)
}
