package dev.marcal.mediapulse.server.integration.spotify

import dev.marcal.mediapulse.server.config.SpotifyProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class SpotifyWebClientConfig(
    private val props: SpotifyProperties,
) {
    @Bean
    fun spotifyApiWebClient(
        @Qualifier("remoteWebClientBuilder") builder: WebClient.Builder,
    ): WebClient =
        builder
            .baseUrl(props.apiBaseUrl)
            .build()

    @Bean
    fun spotifyAccountsWebClient(
        @Qualifier("remoteWebClientBuilder") builder: WebClient.Builder,
    ): WebClient =
        builder
            .baseUrl(props.accountsBaseUrl)
            .build()

    @Bean
    fun spotifyImageWebClient(
        @Qualifier("imagesWebClientBuilder") builder: WebClient.Builder,
    ): WebClient {
        val maxBytes = 10 * 1024 * 1024
        val strategies =
            ExchangeStrategies
                .builder()
                .codecs { it.defaultCodecs().maxInMemorySize(maxBytes) }
                .build()

        return builder
            .exchangeStrategies(strategies)
            .defaultHeader(HttpHeaders.USER_AGENT, "MediaPulse/1.0")
            .build()
    }
}
