package dev.marcal.mediapulse.server.service.pipeline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@EnableScheduling
class DailyImportPipelineScheduler(
    private val runner: ImportPipelineRunner,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Scheduled(cron = "\${media-pulse.pipeline.schedule-cron}")
    fun scheduled() {
        scope.launch {
            runner.run(reason = "daily")
        }
    }
}
