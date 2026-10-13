package dev.marcal.mediapulse.server.integration.steamgriddb

import dev.marcal.mediapulse.server.config.SteamGridDbProperties
import dev.marcal.mediapulse.server.service.image.LibraryImageStorageService
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
    ): WebClient = builder.clone().baseUrl(props.apiBaseUrl).build()

    @Bean
    fun steamGridDbImageWebClient(
        @Qualifier("imagesWebClientBuilder") builder: WebClient.Builder,
    ): WebClient =
        builder
            .clone()
            .codecs { it.defaultCodecs().maxInMemorySize(LibraryImageStorageService.MAX_BYTES) }
            .build()
}
