package dev.marcal.mediapulse.server.service.hardcover

import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverUserBook
import dev.marcal.mediapulse.server.service.dispatch.DispatchResult
import dev.marcal.mediapulse.server.service.dispatch.EventDispatcher
import org.springframework.stereotype.Component

@Component
class HardcoverEventDispatcher(
    private val objectMapper: ObjectMapper,
    private val processor: HardcoverUserBookProcessor,
) : EventDispatcher {
    override val provider: String = "hardcover"

    override suspend fun dispatch(
        payload: String,
        eventId: Long?,
    ): DispatchResult {
        val item = objectMapper.readValue(payload, HardcoverUserBook::class.java)
        processor.process(item)
        return DispatchResult.SUCCESS
    }
}
