package dev.marcal.mediapulse.server.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.time.Duration
import java.util.concurrent.TimeUnit

@Configuration
class WebClientBaseConfig(
    private val props: HttpClientsProperties,
) {
    @Bean
    fun remoteHttpClient(): HttpClient = buildHttpClient("remote", props.remote)

    @Bean
    fun localHttpClient(): HttpClient = buildHttpClient("local", props.local)

    @Bean
    fun imagesHttpClient(): HttpClient = buildHttpClient("images", props.images)

    @Bean("remoteWebClientBuilder")
    @Primary
    fun remoteWebClientBuilder(remoteHttpClient: HttpClient): WebClient.Builder =
        WebClient
            .builder()
            .clientConnector(ReactorClientHttpConnector(remoteHttpClient))

    @Bean("imagesWebClientBuilder")
    fun imagesWebClientBuilder(imagesHttpClient: HttpClient): WebClient.Builder =
        WebClient
            .builder()
            .clientConnector(ReactorClientHttpConnector(imagesHttpClient))

    @Bean("plexWebClientBuilder")
    fun plexWebClientBuilder(localHttpClient: HttpClient): WebClient.Builder {
        val maxBytes = 50 * 1024 * 1024 // 50MB

        val strategies =
            ExchangeStrategies
                .builder()
                .codecs { codecs ->
                    codecs.defaultCodecs().maxInMemorySize(maxBytes)
                }.build()

        return WebClient
            .builder()
            .clientConnector(ReactorClientHttpConnector(localHttpClient))
            .exchangeStrategies(strategies)
    }

    private fun buildHttpClient(
        name: String,
        c: HttpClientsProperties.Client,
    ): HttpClient {
        val provider =
            ConnectionProvider
                .builder(name)
                .maxConnections(c.maxConnections)
                .pendingAcquireTimeout(Duration.ofMillis(c.pendingAcquireTimeoutMs))
                .maxIdleTime(Duration.ofMillis(c.maxIdleTimeMs))
                .maxLifeTime(Duration.ofMillis(c.maxLifeTimeMs))
                .build()

        return HttpClient
            .create(provider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, c.connectTimeoutMs)
            .responseTimeout(Duration.ofMillis(c.responseTimeoutMs))
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(c.readTimeoutMs, TimeUnit.MILLISECONDS))
                conn.addHandlerLast(WriteTimeoutHandler(c.writeTimeoutMs, TimeUnit.MILLISECONDS))
            }
    }
}
