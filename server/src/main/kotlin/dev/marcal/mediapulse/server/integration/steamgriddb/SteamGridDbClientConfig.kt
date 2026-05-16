package dev.marcal.mediapulse.server.integration.steamgriddb

import dev.marcal.mediapulse.server.config.SteamGridDbProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class SteamGridDbClientConfig(
    private val props: SteamGridDbProperties,
) {
    @Bean
    fun steamGridDbWebClient(
        @Qualifier("remoteWebClientBuilder") builder: WebClient.Builder,
    ): WebClient = builder.baseUrl(props.apiBaseUrl).build()
}
