package dev.marcal.mediapulse.server.integration.plex

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class PlexClientConfig {
    @Bean
    fun plexWebClient(
        @Qualifier("plexWebClientBuilder") builder: WebClient.Builder,
    ): WebClient =
        builder
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("X-Plex-Product", "media-pulse")
            .defaultHeader("X-Plex-Version", "1.0")
            .build()
}
