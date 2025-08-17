package dev.marcal.mediapulse.server.controller.eventsource

import dev.marcal.mediapulse.server.controller.dto.ApiResult
import dev.marcal.mediapulse.server.controller.eventsource.dto.ReprocessCounter
import dev.marcal.mediapulse.server.controller.eventsource.dto.ReprocessRequest
import dev.marcal.mediapulse.server.service.eventsource.ReprocessEventSource
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RequestMapping("/event-sources")
@RestController
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
