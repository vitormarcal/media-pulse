package dev.marcal.mediapulse.server.controller

import dev.marcal.mediapulse.server.api.timeline.TimelineDomain
import dev.marcal.mediapulse.server.api.timeline.TimelineKind
import dev.marcal.mediapulse.server.service.timeline.TimelineService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/timeline")
class TimelineController(
    private val service: TimelineService,
) {
    @GetMapping
    fun page(
        @RequestParam(required = false) domain: TimelineDomain?,
        @RequestParam(required = false) kind: TimelineKind?,
        @RequestParam(required = false) month: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "40") limit: Int,
    ) = service.page(domain, kind, month, cursor, limit)

    @GetMapping("/periods")
    fun periods(
        @RequestParam(required = false) domain: TimelineDomain?,
        @RequestParam(required = false) kind: TimelineKind?,
    ) = service.periods(domain, kind)
}
