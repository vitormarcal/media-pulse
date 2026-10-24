package dev.marcal.mediapulse.server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI

@ConfigurationProperties("media-pulse.visual-library")
data class VisualLibraryProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "",
    val migrationEnabled: Boolean = false,
    val migrationBatchSize: Int = 10,
    val migrationIntervalMs: Long = 60_000,
    val token: String = "",
) {
    init {
        require(token.all { it in '!'..'~' }) { "Visual Library token deve ser informado sem espaços, controles ou prefixo Bearer." }
        require(migrationBatchSize in 1..100) { "Visual Library migration-batch-size deve estar entre 1 e 100." }
        require(migrationIntervalMs >= 1_000) { "Visual Library migration-interval-ms deve ser pelo menos 1000." }
        require(!(enabled || migrationEnabled) || baseUrl.isNotBlank()) { "Visual Library habilitado exige base-url." }
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

    override fun toString(): String =
        "VisualLibraryProperties(enabled=$enabled, baseUrl=$baseUrl, migrationEnabled=$migrationEnabled, " +
            "migrationBatchSize=$migrationBatchSize, migrationIntervalMs=$migrationIntervalMs, token=[omitido])"
}
