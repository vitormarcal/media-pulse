package dev.marcal.mediapulse.server.service.eventsource

import dev.marcal.mediapulse.server.controller.eventsource.dto.ReprocessCounter
import dev.marcal.mediapulse.server.controller.eventsource.dto.ReprocessRequest
import dev.marcal.mediapulse.server.model.EventSource
import dev.marcal.mediapulse.server.repository.crud.EventSourceCrudRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Instant
import kotlin.math.ceil

@Service
class ReprocessEventSource(
    private val processEventSourceService: ProcessEventSourceService,
    private val eventSourceCrudRepository: EventSourceCrudRepository,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ReprocessEventSource::class.java)
    }

    fun count(reprocessRequest: ReprocessRequest): ReprocessCounter {
        val pageSize = reprocessRequest.pageSize.takeIf { it in 1..1000 } ?: 1000
        val total =
            eventSourceCrudRepository.countForReprocess(
                all = reprocessRequest.all,
                statuses = reprocessRequest.status,
                providersEmpty = reprocessRequest.providers.isEmpty(),
                providers = reprocessRequest.providers,
            )

        val pages =
            if (total == 0L) 0L else ceil(total.toDouble() / pageSize.toDouble()).toLong()

        return ReprocessCounter(
            total = total,
            pageSize = pageSize,
            pages = pages,
            fromIdExclusive = reprocessRequest.fromIdExclusive,
        )
    }

    fun reprocess(reprocessRequest: ReprocessRequest) {
        logger.info("Reprocessing event sources: $reprocessRequest")

        val counter = count(reprocessRequest)
        if (counter.total == 0L) {
            logger.info("No event sources found for reprocessing: $reprocessRequest")
            return
        }

        val pageSize = counter.pageSize
        var afterId = reprocessRequest.fromIdExclusive
        var processed = 0L
        var batches = 0L

        while (true) {
            val batch =
                eventSourceCrudRepository.findBatchForReprocess(
                    afterId = afterId,
                    all = reprocessRequest.all,
                    statuses = reprocessRequest.status,
                    providersEmpty = reprocessRequest.providers.isEmpty(),
                    providers = reprocessRequest.providers,
                    pageable = PageRequest.of(0, pageSize),
                )

            if (batch.isEmpty()) break

            batches++
            for (es in batch) {
                processEventSourceService.execute(es.id)
                processed++
                afterId = es.id
            }

            logger.info(
                "Reprocess progress: processed=$processed/${counter.total} batches=$batches lastId=$afterId request=$reprocessRequest",
            )
        }

        logger.info("Reprocess finished: processed=$processed totalSnapshot=${counter.total} lastId=$afterId request=$reprocessRequest")
    }

    fun reprocessById(eventSourceId: Long) {
        val event =
            eventSourceCrudRepository
                .findById(eventSourceId)
                .orElseThrow { IllegalArgumentException("EventSource not found: $eventSourceId") }

        val reset =
            event.copy(
                status = EventSource.Status.PENDING,
                errorMessage = null,
                updatedAt = Instant.now(),
            )

        eventSourceCrudRepository.save(reset)
        processEventSourceService.execute(eventSourceId)
    }
}
