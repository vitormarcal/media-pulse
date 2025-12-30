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
    suspend fun reprocess(
        @RequestBody reprocessRequest: ReprocessRequest,
    ): ApiResult<ReprocessCounter> {
        val counter = reprocessEventSource.count(reprocessRequest)
        reprocessEventSource.reprocessAsync(reprocessRequest)
        return ApiResult(counter)
    }

    @PostMapping("/{id}/reprocess")
    fun reprocessById(
        @org.springframework.web.bind.annotation.PathVariable id: Long,
    ): ApiResult<Unit> {
        reprocessEventSource.reprocessById(id)
        return ApiResult(Unit)
    }
}
