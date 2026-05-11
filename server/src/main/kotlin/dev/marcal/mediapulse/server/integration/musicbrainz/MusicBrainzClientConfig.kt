package dev.marcal.mediapulse.server.integration.musicbrainz

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class MusicBrainzClientConfig {
    @Bean
    fun musicBrainzWebClient(builder: WebClient.Builder): WebClient =
        builder
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
}
