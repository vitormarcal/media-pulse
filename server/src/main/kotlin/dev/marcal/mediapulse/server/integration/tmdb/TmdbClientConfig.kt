package dev.marcal.mediapulse.server.integration.tmdb

import dev.marcal.mediapulse.server.config.TmdbProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class TmdbClientConfig(
    private val props: TmdbProperties,
) {
    @Bean
    fun tmdbWebClient(
        @Qualifier("remoteWebClientBuilder") builder: WebClient.Builder,
    ): WebClient {
        val webClientBuilder = builder.baseUrl(props.apiBaseUrl)

        if (props.token.isNotBlank()) {
            webClientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${props.token}")
        }

        return webClientBuilder.build()
    }
}
