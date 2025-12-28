package dev.marcal.mediapulse.server.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "media-pulse.http")
data class HttpClientsProperties(
    val remote: Client = Client(),
    val local: Client = Client(),
) {
    data class Client(
        val maxConnections: Int = 20,
        val pendingAcquireTimeoutMs: Long = 10_000,
        val connectTimeoutMs: Int = 10_000,
        val responseTimeoutMs: Long = 25_000,
        val readTimeoutMs: Long = 25_000,
        val writeTimeoutMs: Long = 25_000,
        val maxIdleTimeMs: Long = 60_000,
        val maxLifeTimeMs: Long = 300_000,
    )
}
