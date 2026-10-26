package dev.marcal.mediapulse.server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.UUID

@ConfigurationProperties("media-pulse.auth")
class OwnerAuthProperties(
    val resetToken: String = "",
    val plexWebhookSecret: String = "",
) {
    init {
        require(
            resetToken.isBlank() || runCatching { UUID.fromString(resetToken).toString() == resetToken.lowercase() }.getOrDefault(false),
        ) {
            "AUTH_RESET_TOKEN must be a UUID"
        }
    }
}
