package dev.marcal.mediapulse.server.service.hardcover

import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.config.HardcoverProperties
import dev.marcal.mediapulse.server.integration.hardcover.HardcoverApiClient
import dev.marcal.mediapulse.server.model.EventSource
import dev.marcal.mediapulse.server.service.eventsource.EventSourceService
import dev.marcal.mediapulse.server.service.eventsource.ProcessEventSourceService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
class HardcoverImportService(
    private val props: HardcoverProperties,
    private val api: HardcoverApiClient,
    private val eventSourceService: EventSourceService,
    private val processEventSourceService: ProcessEventSourceService,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)

    suspend fun importUserBooks(maxPages: Int? = null): Int {
        if (!props.enabled) {
            logger.info("Hardcover import disabled | ignored")
            return 0
        }
        if (props.userId <= 0) {
            logger.info("Hardcover import missing userId | ignored")
            return 0
        }
        if (!running.compareAndSet(false, true)) {
            logger.info("Hardcover import already running | ignored")
            return -1
        }

        val runId =
            java.util.UUID
                .randomUUID()
                .toString()
                .take(8)
        val start = System.currentTimeMillis()
        logger.info("Hardcover import started | runId={} maxPages={}", runId, maxPages)

        var processed = 0
        var ignored = 0
        var failed = 0
        var fetched = 0
        try {
            val pageSize = props.poll.pageSize.coerceAtLeast(1)
            var offset = 0
            var pages = 0

            while (true) {
                if (maxPages != null && pages >= maxPages) break

                val items = api.fetchUserBooksPage(props.userId, pageSize, offset)
                if (items.isEmpty()) break

                fetched += items.size
                for (item in items) {
                    val payload = objectMapper.writeValueAsString(item)
                    val event =
                        eventSourceService.save(
                            provider = "hardcover",
                            payload = payload,
                        )

                    if (event.status == EventSource.Status.SUCCESS) {
                        ignored++
                        continue
                    }

                    try {
                        processEventSourceService.execute(event.id)
                        processed++
                    } catch (ex: Exception) {
                        failed++
                    }
                }

                offset += items.size
                pages++
            }

            logger.info(
                "Hardcover import stats | runId={} fetched={} processed={} ignored={} failed={}"
                    .trimIndent(),
                runId,
                fetched,
                processed,
                ignored,
                failed,
            )

            return processed
        } finally {
            logger.info("Hardcover import finished | runId={} elapsedMs={}", runId, System.currentTimeMillis() - start)
            running.set(false)
        }
    }
}
