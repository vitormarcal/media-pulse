package dev.marcal.mediapulse.server.service.books

import dev.marcal.mediapulse.server.config.VisualLibraryProperties
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
class BookSeriesTagSyncRunner(
    private val service: BookSeriesTagSyncService,
    private val properties: VisualLibraryProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val worker = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "book-series-tags").apply { isDaemon = true } }

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        if (properties.baseUrl.isBlank()) return
        worker.scheduleWithFixedDelay({
            try {
                service.runBatch()
            } catch (error: Exception) {
                logger.warn("Lote de tags de séries interrompido; nova tentativa posterior. error={}", error.javaClass.simpleName)
            }
        }, 0, 5, TimeUnit.SECONDS)
    }

    @PreDestroy
    fun stop() {
        worker.shutdownNow()
    }
}
