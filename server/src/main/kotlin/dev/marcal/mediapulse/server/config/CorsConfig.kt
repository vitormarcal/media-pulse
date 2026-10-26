package dev.marcal.mediapulse.server.config

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@Configuration
class CorsConfig(
    private val props: CorsProperties,
) {
    @Bean
    fun corsFilter(): FilterRegistrationBean<CorsFilter> {
        val configuration =
            CorsConfiguration().apply {
                allowedOrigins = props.allowedOrigin
                allowedMethods = listOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = true
            }
        val source = UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/api/**", configuration) }
        // Resolve the external HTTPS origin before CORS, and authorize only after CORS.
        return FilterRegistrationBean(CorsFilter(source)).apply { order = Ordered.HIGHEST_PRECEDENCE + 10 }
    }
}
