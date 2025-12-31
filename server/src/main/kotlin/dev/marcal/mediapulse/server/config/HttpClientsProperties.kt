package dev.marcal.mediapulse.server.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "media-pulse.http")
data class HttpClientsProperties(
    val remote: Client = Client(),
    val local: Client = Client(),
    val images: Client =
        Client(
            maxConnections = 20,
            pendingAcquireTimeoutMs = 10_000,
            connectTimeoutMs = 3_000,
            responseTimeoutMs = 15_000,
            readTimeoutMs = 15_000,
            writeTimeoutMs = 15_000,
            maxIdleTimeMs = 60_000,
            maxLifeTimeMs = 300_000,
        ),
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
