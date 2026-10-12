package dev.marcal.mediapulse.server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties("media-pulse.visual-library")
data class VisualLibraryProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "",
) {
    init {
        require(!enabled || baseUrl.isNotBlank()) { "Visual Library habilitado exige base-url." }
        if (baseUrl.isNotBlank()) {
            val uri = URI(baseUrl)
            require(
                uri.scheme in setOf("http", "https") &&
                    uri.host != null &&
                    uri.userInfo == null &&
                    uri.query == null &&
                    uri.fragment == null,
            ) {
                "Visual Library base-url deve ser uma URL HTTP/HTTPS sem credenciais, query ou fragmento."
            }
        }
    }
}
