package dev.marcal.mediapulse.server.integration.musicbrainz

import dev.marcal.mediapulse.server.config.MusicBrainzProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class MusicBrainzClientConfig {
    @Bean
    fun musicBrainzWebClient(
        builder: WebClient.Builder,
        props: MusicBrainzProperties,
    ): WebClient =
        builder
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.USER_AGENT, props.userAgent)
            .build()
}
