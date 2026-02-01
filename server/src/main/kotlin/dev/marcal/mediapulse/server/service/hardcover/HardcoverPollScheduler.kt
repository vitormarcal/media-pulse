package dev.marcal.mediapulse.server.service.hardcover

import dev.marcal.mediapulse.server.config.HardcoverProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class HardcoverPollScheduler(
    private val props: HardcoverProperties,
    private val importService: HardcoverImportService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Scheduled(cron = "\${media-pulse.hardcover.poll.cron}")
    fun scheduled() {
        if (!props.enabled) return
        if (!props.poll.enabled) return
        if (props.userId <= 0) return

        scope.launch {
            try {
                val imported = importService.importUserBooks()
                logger.info("Hardcover poll done | imported={}", imported)
            } catch (e: Exception) {
                logger.error("Hardcover poll failed", e)
            }
        }
    }
}
