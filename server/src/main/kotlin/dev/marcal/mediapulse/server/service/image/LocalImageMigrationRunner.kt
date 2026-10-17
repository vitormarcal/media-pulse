package dev.marcal.mediapulse.server.service.image

import dev.marcal.mediapulse.server.config.VisualLibraryProperties
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Component
class LocalImageMigrationRunner(
    private val properties: VisualLibraryProperties,
    private val migration: LocalImageMigrationService,
) {
    private val started = AtomicBoolean(false)
    private val logger = LoggerFactory.getLogger(javaClass)
    private val worker =
        Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "visual-library-migration").apply {
                isDaemon =
                    true
            }
        }

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        if (!properties.migrationEnabled || !started.compareAndSet(false, true)) return
        logger.info("Migração local para Visual Library habilitada; lotes de {} imagens.", properties.migrationBatchSize)
        worker.scheduleWithFixedDelay(
            {
                try {
                    migration.runBatch()
                } catch (error: Exception) {
                    logger.warn("Lote de migração interrompido; nova tentativa posterior. erro={}", error.javaClass.simpleName)
                }
            },
            0,
            properties.migrationIntervalMs,
            TimeUnit.MILLISECONDS,
        )
    }

    @PreDestroy
    fun stop() {
        worker.shutdownNow()
    }
}
