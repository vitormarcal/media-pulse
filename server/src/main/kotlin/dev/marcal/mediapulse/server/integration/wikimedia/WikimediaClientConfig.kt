package dev.marcal.mediapulse.server.integration.wikimedia

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Configuration
class WikimediaClientConfig {
    @Bean
    @Qualifier("wikimediaWebClient")
    fun wikimediaWebClient(builder: WebClient.Builder): WebClient =
        builder
            .clone()
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.USER_AGENT, "MediaPulse/1.0 (personal media archive)")
            .filter(
                ExchangeFilterFunction.ofRequestProcessor { request ->
                    Mono.just(
                        ClientRequest
                            .from(request)
                            .headers { it.remove(HttpHeaders.AUTHORIZATION) }
                            .build(),
                    )
                },
            ).codecs { it.defaultCodecs().maxInMemorySize(12 * 1024 * 1024) }
            .build()
}
