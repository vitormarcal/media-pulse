package dev.marcal.mediapulse.server.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig(
    @Value("\${media-pulse.allowed-origin}") private val allowedOrigin: List<String>,
) {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    @Bean
    fun corsConfigurer(): WebMvcConfigurer {
        logger.info("AllowedOrigins: $allowedOrigin")
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry
                    .addMapping("/**")
                    .allowedMethods("*")
                    .allowedOrigins(*allowedOrigin.toTypedArray())
            }
        }
    }
}
