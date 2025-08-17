package dev.marcal.mediapulse.server.service.eventsource

import dev.marcal.mediapulse.server.MediapulseServerApplicationTests
import dev.marcal.mediapulse.server.controller.dto.eventsource.ReprocessRequest
import dev.marcal.mediapulse.server.fixture.EventSourceFixture
import dev.marcal.mediapulse.server.model.EventSource
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ReprocessEventSourceIT : MediapulseServerApplicationTests() {
    @Autowired
    private lateinit var reprocessEventSource: ReprocessEventSource

    @Test
    fun `should reprocess event source successfully`() {
        val example1 = eventSourceCrudRepository.save(EventSourceFixture.example(example = 1, status = EventSource.Status.FAILED))
        val example2 = eventSourceCrudRepository.save(EventSourceFixture.example(example = 2, status = EventSource.Status.PENDING))
        val example3 = eventSourceCrudRepository.save(EventSourceFixture.example(example = 3, status = EventSource.Status.PENDING))

        reprocessEventSource.reprocess(ReprocessRequest(all = true))

        val exampleReprocessed1 = eventSourceCrudRepository.findById(example1.id).orElseThrow()
        val exampleReprocessed2 = eventSourceCrudRepository.findById(example2.id).orElseThrow()
        val exampleReprocessed3 = eventSourceCrudRepository.findById(example3.id).orElseThrow()

        Thread.sleep(1000)
        Assertions.assertEquals(EventSource.Status.SUCCESS, exampleReprocessed1.status)
        Assertions.assertEquals(EventSource.Status.FAILED, exampleReprocessed2.status)
        Assertions.assertEquals(EventSource.Status.FAILED, exampleReprocessed3.status)
    }
}
