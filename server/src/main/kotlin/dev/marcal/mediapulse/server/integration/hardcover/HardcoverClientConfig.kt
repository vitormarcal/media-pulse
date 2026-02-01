package dev.marcal.mediapulse.server.integration.hardcover

import dev.marcal.mediapulse.server.config.HardcoverProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class HardcoverClientConfig(
    private val props: HardcoverProperties,
) {
    @Bean
    fun hardcoverWebClient(
        @Qualifier("remoteWebClientBuilder") builder: WebClient.Builder,
    ): WebClient {
        val maxBytes = 20 * 1024 * 1024
        val strategies =
            ExchangeStrategies
                .builder()
                .codecs { it.defaultCodecs().maxInMemorySize(maxBytes) }
                .build()

        val clientBuilder =
            builder
                .baseUrl(props.apiBaseUrl)
                .exchangeStrategies(strategies)

        if (props.token.isNotBlank()) {
            clientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, props.token)
        }

        return clientBuilder.build()
    }

    @Bean
    fun hardcoverImageWebClient(
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
