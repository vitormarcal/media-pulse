package dev.marcal.mediapulse.server.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class BackgroundExecutorConfig {
    @Bean("backgroundExecutor")
    fun backgroundExecutor(): TaskExecutor {
        val ex = ThreadPoolTaskExecutor()
        ex.corePoolSize = 2
        ex.maxPoolSize = 8
        ex.queueCapacity = 1000
        ex.setThreadNamePrefix("bg-")
        ex.initialize()
        return ex
    }
}
