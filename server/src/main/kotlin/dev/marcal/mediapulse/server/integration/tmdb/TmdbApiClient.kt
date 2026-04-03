package dev.marcal.mediapulse.server.integration.tmdb

import com.fasterxml.jackson.annotation.JsonProperty
import dev.marcal.mediapulse.server.config.TmdbProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono

data class TmdbMovieDetailsResponse(
    val title: String? = null,
    @JsonProperty("original_title")
    val originalTitle: String? = null,
    val overview: String? = null,
    @JsonProperty("release_date")
    val releaseDate: String? = null,
    @JsonProperty("poster_path")
    val posterPath: String? = null,
    @JsonProperty("backdrop_path")
    val backdropPath: String? = null,
)

data class TmdbShowDetailsResponse(
    val name: String? = null,
    @JsonProperty("original_name")
    val originalName: String? = null,
    val overview: String? = null,
    @JsonProperty("first_air_date")
    val firstAirDate: String? = null,
    @JsonProperty("poster_path")
    val posterPath: String? = null,
    @JsonProperty("backdrop_path")
    val backdropPath: String? = null,
)

@Component
class TmdbApiClient(
    private val tmdbWebClient: WebClient,
    private val tmdbProperties: TmdbProperties,
    private val tmdbRateLimiter: TmdbRateLimiter,
) {
    data class TmdbMovieDetails(
        val title: String?,
        val originalTitle: String?,
        val overview: String?,
        val releaseYear: Int?,
        val posterPath: String?,
        val backdropPath: String?,
    )

    data class TmdbShowDetails(
        val title: String?,
        val originalTitle: String?,
        val overview: String?,
        val firstAirYear: Int?,
        val posterPath: String?,
        val backdropPath: String?,
    )

    private val logger = LoggerFactory.getLogger(javaClass)

    fun fetchMovieDetails(tmdbId: String): TmdbMovieDetails? {
        if (!tmdbProperties.enabled) return null

        val attempts = (tmdbProperties.max429Retries + 1).coerceAtLeast(1)
        var attempt = 0

        while (attempt < attempts) {
            attempt++
            tmdbRateLimiter.acquire(tmdbProperties.rateLimitPerSecond)

            try {
                val response =
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
                        .block() ?: return null

                return TmdbMovieDetails(
                    title = response.title?.trim()?.ifBlank { null },
                    originalTitle = response.originalTitle?.trim()?.ifBlank { null },
                    overview = response.overview?.trim()?.ifBlank { null },
                    releaseYear = parseReleaseYear(response.releaseDate),
                    posterPath = response.posterPath?.trim()?.ifBlank { null },
                    backdropPath = response.backdropPath?.trim()?.ifBlank { null },
                )
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

    fun fetchShowDetails(tmdbId: String): TmdbShowDetails? {
        if (!tmdbProperties.enabled) return null

        val attempts = (tmdbProperties.max429Retries + 1).coerceAtLeast(1)
        var attempt = 0

        while (attempt < attempts) {
            attempt++
            tmdbRateLimiter.acquire(tmdbProperties.rateLimitPerSecond)

            try {
                val response =
                    tmdbWebClient
                        .get()
                        .uri { uriBuilder ->
                            val builder = uriBuilder.path("/tv/{id}")
                            if (tmdbProperties.token.isBlank() && tmdbProperties.apiKey.isNotBlank()) {
                                builder.queryParam("api_key", tmdbProperties.apiKey)
                            }
                            builder.build(tmdbId)
                        }.retrieve()
                        .bodyToMono<TmdbShowDetailsResponse>()
                        .block() ?: return null

                return TmdbShowDetails(
                    title = response.name?.trim()?.ifBlank { null },
                    originalTitle = response.originalName?.trim()?.ifBlank { null },
                    overview = response.overview?.trim()?.ifBlank { null },
                    firstAirYear = parseReleaseYear(response.firstAirDate),
                    posterPath = response.posterPath?.trim()?.ifBlank { null },
                    backdropPath = response.backdropPath?.trim()?.ifBlank { null },
                )
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
                logger.warn("Failed to fetch TMDb show details for id={} status={}", tmdbId, ex.statusCode.value(), ex)
                return null
            } catch (ex: Exception) {
                logger.warn("Failed to fetch TMDb show details for id={}", tmdbId, ex)
                return null
            }
        }

        return null
    }

    private fun parseReleaseYear(releaseDate: String?): Int? {
        val value = releaseDate?.trim()?.ifBlank { null } ?: return null
        if (value.length < 4) return null
        return value.substring(0, 4).toIntOrNull()
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
