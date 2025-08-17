package dev.marcal.mediapulse.server.service.eventsource

import dev.marcal.mediapulse.server.controller.dto.eventsource.ReprocessCounter
import dev.marcal.mediapulse.server.controller.dto.eventsource.ReprocessRequest
import dev.marcal.mediapulse.server.model.EventSource
import dev.marcal.mediapulse.server.repository.crud.EventSourceCrudRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class ReprocessEventSource(
    private val processEventSourceService: ProcessEventSourceService,
    private val eventSourceCrudRepository: EventSourceCrudRepository,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
    }

    fun count(reprocessRequest: ReprocessRequest): ReprocessCounter {
        val total =
            if (reprocessRequest.all) {
                eventSourceCrudRepository.count()
            } else {
                eventSourceCrudRepository.countByStatusIn(reprocessRequest.status)
            }

        val pageSize = reprocessRequest.pageSize.takeIf { it > 0 && it <= 1000 } ?: 1000
        val pages = total / pageSize + 1

        return ReprocessCounter(
            total = total,
            pageSize = pageSize,
            pages = pages,
            fromPage = reprocessRequest.fromPage,
        )
    }

    @Async
    fun reprocessAsync(reprocessRequest: ReprocessRequest) {
        logger.info("Reprocessing event sources asynchronously with request: $reprocessRequest")
        reprocess(reprocessRequest)
    }

    fun reprocess(reprocessRequest: ReprocessRequest) {
        logger.info("Reprocessing event sources with request: $reprocessRequest")

        count(reprocessRequest).let { counter ->
            if (counter.total == 0L) {
                logger.info("No event sources found for reprocessing with request: $reprocessRequest")
                return
            }

            var page = reprocessRequest.fromPage

            while (page < counter.pages) {
                val pageRequest = PageRequest.of(page, counter.pageSize)
                findByFilter(reprocessRequest, pageRequest).forEach { eventSource ->
                    processEventSourceService.execute(eventSource.id)
                }
                page++
                logger.info("Reprocessed page $page of $${counter.pages} with request: $reprocessRequest")
            }
        }
    }

    private fun findByFilter(
        reprocessRequest: ReprocessRequest,
        pageRequest: PageRequest,
    ): Page<EventSource> {
        if (reprocessRequest.all) {
            return eventSourceCrudRepository.findAll(pageRequest)
        }
        return eventSourceCrudRepository.findAllByStatusIn(reprocessRequest.status, pageRequest)
    }
}
