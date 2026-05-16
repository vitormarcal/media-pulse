package dev.marcal.mediapulse.server.integration.igdb

import dev.marcal.mediapulse.server.config.IgdbProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class IgdbClientConfig(
    private val props: IgdbProperties,
) {
    @Bean
    fun igdbWebClient(
        @Qualifier("remoteWebClientBuilder") builder: WebClient.Builder,
    ): WebClient = builder.baseUrl(props.apiBaseUrl).build()

    @Bean
    fun igdbOAuthWebClient(
        @Qualifier("remoteWebClientBuilder") builder: WebClient.Builder,
    ): WebClient = builder.baseUrl(props.oauthBaseUrl).build()
}
