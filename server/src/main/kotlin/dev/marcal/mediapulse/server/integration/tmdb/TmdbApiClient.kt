package dev.marcal.mediapulse.server.integration.tmdb

import com.fasterxml.jackson.annotation.JsonProperty
import dev.marcal.mediapulse.server.config.TmdbProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono

data class TmdbMovieDetailsResponse(
    @JsonProperty("poster_path")
    val posterPath: String? = null,
)

@Component
class TmdbApiClient(
    private val tmdbWebClient: WebClient,
    private val tmdbProperties: TmdbProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun fetchPosterPath(tmdbId: String): String? {
        if (!tmdbProperties.enabled) return null

        return try {
            tmdbWebClient
                .get()
                .uri { uriBuilder ->
                    val builder = uriBuilder.path("/movie/{id}")
                    if (tmdbProperties.token.isBlank() && tmdbProperties.apiKey.isNotBlank()) {
                        builder.queryParam("api_key", tmdbProperties.apiKey)
                    }
                    builder.build(tmdbId)
                }.retrieve()
                .bodyToMono<TmdbMovieDetailsResponse>()
                .block()
                ?.posterPath
                ?.trim()
                ?.ifBlank { null }
        } catch (ex: WebClientResponseException.NotFound) {
            null
        } catch (ex: Exception) {
            logger.warn("Failed to fetch TMDb details for id={}", tmdbId, ex)
            null
        }
    }
}
