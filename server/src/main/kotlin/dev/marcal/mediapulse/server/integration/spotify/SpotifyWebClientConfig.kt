package dev.marcal.mediapulse.server.integration.spotify

import dev.marcal.mediapulse.server.config.SpotifyProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class SpotifyWebClientConfig(
    private val props: SpotifyProperties,
) {
    @Bean
    fun spotifyApiWebClient(): WebClient =
        WebClient
            .builder()
            .baseUrl(props.apiBaseUrl)
            .build()

    @Bean
    fun spotifyAccountsWebClient(): WebClient =
        WebClient
            .builder()
            .baseUrl(props.accountsBaseUrl)
            .build()
}
