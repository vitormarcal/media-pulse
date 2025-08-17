package dev.marcal.mediapulse.server.controller

import dev.marcal.mediapulse.server.controller.dto.ApiResult
import dev.marcal.mediapulse.server.controller.dto.eventsource.ReprocessCounter
import dev.marcal.mediapulse.server.controller.dto.eventsource.ReprocessRequest
import dev.marcal.mediapulse.server.service.eventsource.ReprocessEventSource
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping

@RequestMapping("/event-sources")
class EventSourceController(
    private val reprocessEventSource: ReprocessEventSource,
) {
    @PostMapping("/reprocess")
    fun reprocess(
        @RequestBody reprocessRequest: ReprocessRequest,
    ): ApiResult<ReprocessCounter> {
        val counter = reprocessEventSource.count(reprocessRequest)
        reprocessEventSource.reprocessAsync(reprocessRequest)
        return ApiResult(counter)
    }
}
