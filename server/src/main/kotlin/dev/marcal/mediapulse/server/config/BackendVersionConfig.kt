package dev.marcal.mediapulse.server.config

import dev.marcal.mediapulse.server.Boot
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class BackendVersionConfig {
    @Bean
    fun backendVersion(buildPropertiesProvider: ObjectProvider<BuildProperties>): BackendVersion {
        val version =
            buildPropertiesProvider.ifAvailable?.version
                ?: Boot::class.java.`package`?.implementationVersion
                ?: "dev"

        return BackendVersion(version)
    }
}

data class BackendVersion(
    val value: String,
) {
    val userAgent: String = "MediaPulse/$value"
}
