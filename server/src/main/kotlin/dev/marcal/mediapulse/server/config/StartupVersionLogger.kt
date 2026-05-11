package dev.marcal.mediapulse.server.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class StartupVersionLogger(
    private val backendVersion: BackendVersion,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        logger.info("MediaPulse backend starting | version={}", backendVersion.value)
    }
}
