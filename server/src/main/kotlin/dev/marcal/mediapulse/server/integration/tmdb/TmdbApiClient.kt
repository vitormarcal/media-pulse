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
    private val tmdbRateLimiter: TmdbRateLimiter,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun fetchPosterPath(tmdbId: String): String? {
        if (!tmdbProperties.enabled) return null

        val attempts = (tmdbProperties.max429Retries + 1).coerceAtLeast(1)
        var attempt = 0

        while (attempt < attempts) {
            attempt++
            tmdbRateLimiter.acquire(tmdbProperties.rateLimitPerSecond)

            try {
                return tmdbWebClient
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
                return null
            } catch (ex: WebClientResponseException) {
                val isTooManyRequests = ex.statusCode.value() == 429
                val hasRemainingRetry = attempt < attempts
                if (isTooManyRequests && hasRemainingRetry) {
                    val waitMs = resolve429WaitMs(ex, attempt)
                    logger.warn("TMDb rate limited (429). Waiting {}ms before retry {}/{}", waitMs, attempt + 1, attempts)
                    Thread.sleep(waitMs)
                    continue
                }
                logger.warn("Failed to fetch TMDb details for id={} status={}", tmdbId, ex.statusCode.value(), ex)
                return null
            } catch (ex: Exception) {
                logger.warn("Failed to fetch TMDb details for id={}", tmdbId, ex)
                return null
            }
        }

        return null
    }

    private fun resolve429WaitMs(
        ex: WebClientResponseException,
        attempt: Int,
    ): Long {
        val retryAfterHeader = ex.headers.getFirst("Retry-After")?.trim()
        val retryAfterSeconds = retryAfterHeader?.toLongOrNull()
        if (retryAfterSeconds != null && retryAfterSeconds >= 0) {
            return retryAfterSeconds * 1000
        }

        val base = tmdbProperties.retryBackoffMs.coerceAtLeast(100)
        return base * attempt
    }
}
