package dev.marcal.mediapulse.server.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "media-pulse.hardcover")
data class HardcoverProperties(
    val enabled: Boolean = false,
    val apiBaseUrl: String = "https://api.hardcover.app/v1/graphql",
    val token: String,
    val userId: Long = 0,
    val poll: Poll = Poll(),
) {
    data class Poll(
        val enabled: Boolean = true,
        val cron: String = "0 */15 * * * *",
        val pageSize: Int = 100,
    )
}
