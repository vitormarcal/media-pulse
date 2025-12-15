package dev.marcal.mediapulse.server.service.pipeline

import dev.marcal.mediapulse.server.config.PipelineProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class StartupPipelineRunner(
    private val pipelineProps: PipelineProperties,
    private val runner: ImportPipelineRunner,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        if (!pipelineProps.enabled || !pipelineProps.runOnStartup) return

        scope.launch {
            runner.run(reason = "startup")
        }
    }
}
