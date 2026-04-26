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
    @JsonProperty("imdb_id")
    val imdbId: String? = null,
    val overview: String? = null,
    @JsonProperty("release_date")
    val releaseDate: String? = null,
    @JsonProperty("poster_path")
    val posterPath: String? = null,
    @JsonProperty("backdrop_path")
    val backdropPath: String? = null,
    @JsonProperty("belongs_to_collection")
    val belongsToCollection: TmdbMovieCollectionResponse? = null,
    val genres: List<TmdbNamedItemResponse> = emptyList(),
)

data class TmdbNamedItemResponse(
    val id: Long? = null,
    val name: String? = null,
)

data class TmdbMovieKeywordsResponse(
    val id: Long? = null,
    val keywords: List<TmdbNamedItemResponse> = emptyList(),
)

data class TmdbMovieCollectionResponse(
    val id: Long? = null,
    val name: String? = null,
    @JsonProperty("poster_path")
    val posterPath: String? = null,
    @JsonProperty("backdrop_path")
    val backdropPath: String? = null,
)

data class TmdbMovieSearchItemResponse(
    val id: Long,
    val title: String? = null,
    @JsonProperty("original_title")
    val originalTitle: String? = null,
    val overview: String? = null,
    @JsonProperty("release_date")
    val releaseDate: String? = null,
    @JsonProperty("poster_path")
    val posterPath: String? = null,
)

data class TmdbMovieSearchResponse(
    val results: List<TmdbMovieSearchItemResponse> = emptyList(),
)

data class TmdbMovieCollectionDetailsResponse(
    val id: Long? = null,
    val name: String? = null,
    val overview: String? = null,
    @JsonProperty("poster_path")
    val posterPath: String? = null,
    @JsonProperty("backdrop_path")
    val backdropPath: String? = null,
    val parts: List<TmdbMovieCollectionPartResponse> = emptyList(),
)

data class TmdbMovieCollectionPartResponse(
    val id: Long? = null,
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

data class TmdbShowSearchItemResponse(
    val id: Long,
    val name: String? = null,
    @JsonProperty("original_name")
    val originalName: String? = null,
    val overview: String? = null,
    @JsonProperty("first_air_date")
    val firstAirDate: String? = null,
    @JsonProperty("poster_path")
    val posterPath: String? = null,
)

data class TmdbShowSearchResponse(
    val results: List<TmdbShowSearchItemResponse> = emptyList(),
)

data class TmdbShowSeasonSummaryResponse(
    val id: Long? = null,
    val name: String? = null,
    @JsonProperty("season_number")
    val seasonNumber: Int? = null,
    @JsonProperty("episode_count")
    val episodeCount: Int? = null,
    @JsonProperty("air_date")
    val airDate: String? = null,
    @JsonProperty("poster_path")
    val posterPath: String? = null,
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
    val seasons: List<TmdbShowSeasonSummaryResponse> = emptyList(),
)

data class TmdbShowSeasonEpisodeResponse(
    val id: Long? = null,
    val name: String? = null,
    val overview: String? = null,
    @JsonProperty("episode_number")
    val episodeNumber: Int? = null,
    @JsonProperty("air_date")
    val airDate: String? = null,
    val runtime: Int? = null,
)

data class TmdbShowSeasonDetailsResponse(
    val id: Long? = null,
    val name: String? = null,
    val overview: String? = null,
    @JsonProperty("season_number")
    val seasonNumber: Int? = null,
    @JsonProperty("air_date")
    val airDate: String? = null,
    @JsonProperty("poster_path")
    val posterPath: String? = null,
    val episodes: List<TmdbShowSeasonEpisodeResponse> = emptyList(),
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
        val imdbId: String?,
        val overview: String?,
        val releaseYear: Int?,
        val posterPath: String?,
        val backdropPath: String?,
        val genres: List<String> = emptyList(),
        val keywords: List<String> = emptyList(),
        val collection: TmdbMovieCollection? = null,
    )

    data class TmdbMovieCollection(
        val tmdbId: String,
        val name: String,
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
        val seasons: List<TmdbShowSeasonSummary>,
    )

    data class TmdbShowSeasonSummary(
        val tmdbId: String?,
        val title: String?,
        val seasonNumber: Int?,
        val episodeCount: Int?,
        val airDate: String?,
        val posterPath: String?,
    )

    data class TmdbShowSeasonEpisode(
        val tmdbId: String?,
        val title: String?,
        val overview: String?,
        val episodeNumber: Int?,
        val airDate: String?,
        val runtimeMinutes: Int?,
    )

    data class TmdbShowSeasonDetails(
        val tmdbId: String?,
        val title: String?,
        val overview: String?,
        val seasonNumber: Int?,
        val airDate: String?,
        val posterPath: String?,
        val episodes: List<TmdbShowSeasonEpisode>,
    )

    data class TmdbMovieSearchItem(
        val tmdbId: String,
        val title: String?,
        val originalTitle: String?,
        val overview: String?,
        val releaseYear: Int?,
        val posterPath: String?,
    )

    data class TmdbMovieCollectionDetails(
        val tmdbId: String,
        val name: String,
        val overview: String?,
        val posterPath: String?,
        val backdropPath: String?,
        val parts: List<TmdbMovieCollectionPart>,
    )

    data class TmdbMovieCollectionPart(
        val tmdbId: String,
        val title: String?,
        val originalTitle: String?,
        val overview: String?,
        val releaseYear: Int?,
        val posterPath: String?,
        val backdropPath: String?,
    )

    data class TmdbShowSearchItem(
        val tmdbId: String,
        val title: String?,
        val originalTitle: String?,
        val overview: String?,
        val firstAirYear: Int?,
        val posterPath: String?,
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

                val keywords = fetchMovieKeywords(tmdbId)

                return TmdbMovieDetails(
                    title = response.title?.trim()?.ifBlank { null },
                    originalTitle = response.originalTitle?.trim()?.ifBlank { null },
                    imdbId = response.imdbId?.trim()?.ifBlank { null },
                    overview = response.overview?.trim()?.ifBlank { null },
                    releaseYear = parseReleaseYear(response.releaseDate),
                    posterPath = response.posterPath?.trim()?.ifBlank { null },
                    backdropPath = response.backdropPath?.trim()?.ifBlank { null },
                    genres = response.genres.mapNotNull { it.name?.trim()?.ifBlank { null } }.distinct(),
                    keywords = keywords,
                    collection =
                        response.belongsToCollection
                            ?.takeIf { it.id != null && !it.name.isNullOrBlank() }
                            ?.let { collection ->
                                TmdbMovieCollection(
                                    tmdbId = collection.id.toString(),
                                    name = collection.name!!.trim(),
                                    posterPath = collection.posterPath?.trim()?.ifBlank { null },
                                    backdropPath = collection.backdropPath?.trim()?.ifBlank { null },
                                )
                            },
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

    private fun fetchMovieKeywords(tmdbId: String): List<String> {
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
                            val builder = uriBuilder.path("/movie/{id}/keywords")
                            if (tmdbProperties.token.isBlank() && tmdbProperties.apiKey.isNotBlank()) {
                                builder.queryParam("api_key", tmdbProperties.apiKey)
                            }
                            builder.build(tmdbId)
                        }.retrieve()
                        .bodyToMono<TmdbMovieKeywordsResponse>()
                        .block()
                        ?: return emptyList()

                return response.keywords.mapNotNull { it.name?.trim()?.ifBlank { null } }.distinct()
            } catch (ex: WebClientResponseException.NotFound) {
                return emptyList()
            } catch (ex: WebClientResponseException) {
                val isTooManyRequests = ex.statusCode.value() == 429
                val hasRemainingRetry = attempt < attempts
                if (isTooManyRequests && hasRemainingRetry) {
                    val waitMs = resolve429WaitMs(ex, attempt)
                    logger.warn("TMDb keywords rate limited (429). Waiting {}ms before retry {}/{}", waitMs, attempt + 1, attempts)
                    Thread.sleep(waitMs)
                    continue
                }
                logger.warn("Failed to fetch TMDb keywords for id={} status={}", tmdbId, ex.statusCode.value(), ex)
                return emptyList()
            } catch (ex: Exception) {
                logger.warn("Failed to fetch TMDb keywords for id={}", tmdbId, ex)
                return emptyList()
            }
        }

        return emptyList()
    }

    fun fetchMovieCollectionDetails(tmdbCollectionId: String): TmdbMovieCollectionDetails? {
        if (!tmdbProperties.enabled) return null

        val normalizedTmdbCollectionId = tmdbCollectionId.trim()
        if (normalizedTmdbCollectionId.isBlank()) return null

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
                            val builder = uriBuilder.path("/collection/{id}")
                            if (tmdbProperties.token.isBlank() && tmdbProperties.apiKey.isNotBlank()) {
                                builder.queryParam("api_key", tmdbProperties.apiKey)
                            }
                            builder.build(normalizedTmdbCollectionId)
                        }.retrieve()
                        .bodyToMono<TmdbMovieCollectionDetailsResponse>()
                        .block() ?: return null

                val resolvedName = response.name?.trim()?.ifBlank { null } ?: return null

                return TmdbMovieCollectionDetails(
                    tmdbId = response.id?.toString() ?: normalizedTmdbCollectionId,
                    name = resolvedName,
                    overview = response.overview?.trim()?.ifBlank { null },
                    posterPath = response.posterPath?.trim()?.ifBlank { null },
                    backdropPath = response.backdropPath?.trim()?.ifBlank { null },
                    parts =
                        response.parts
                            .mapNotNull { part ->
                                val partId = part.id?.toString() ?: return@mapNotNull null
                                TmdbMovieCollectionPart(
                                    tmdbId = partId,
                                    title = part.title?.trim()?.ifBlank { null },
                                    originalTitle = part.originalTitle?.trim()?.ifBlank { null },
                                    overview = part.overview?.trim()?.ifBlank { null },
                                    releaseYear = parseReleaseYear(part.releaseDate),
                                    posterPath = part.posterPath?.trim()?.ifBlank { null },
                                    backdropPath = part.backdropPath?.trim()?.ifBlank { null },
                                )
                            },
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
                logger.warn(
                    "Failed to fetch TMDb movie collection details for id={} status={}",
                    normalizedTmdbCollectionId,
                    ex.statusCode.value(),
                    ex,
                )
                return null
            } catch (ex: Exception) {
                logger.warn("Failed to fetch TMDb movie collection details for id={}", normalizedTmdbCollectionId, ex)
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
                    seasons =
                        response.seasons.map { season ->
                            TmdbShowSeasonSummary(
                                tmdbId = season.id?.toString(),
                                title = season.name?.trim()?.ifBlank { null },
                                seasonNumber = season.seasonNumber,
                                episodeCount = season.episodeCount?.takeIf { it >= 0 },
                                airDate = season.airDate?.trim()?.ifBlank { null },
                                posterPath = season.posterPath?.trim()?.ifBlank { null },
                            )
                        },
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

    fun fetchShowSeasonDetails(
        tmdbId: String,
        seasonNumber: Int,
    ): TmdbShowSeasonDetails? {
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
                            val builder = uriBuilder.path("/tv/{id}/season/{seasonNumber}")
                            if (tmdbProperties.token.isBlank() && tmdbProperties.apiKey.isNotBlank()) {
                                builder.queryParam("api_key", tmdbProperties.apiKey)
                            }
                            builder.build(tmdbId, seasonNumber)
                        }.retrieve()
                        .bodyToMono<TmdbShowSeasonDetailsResponse>()
                        .block() ?: return null

                return TmdbShowSeasonDetails(
                    tmdbId = response.id?.toString(),
                    title = response.name?.trim()?.ifBlank { null },
                    overview = response.overview?.trim()?.ifBlank { null },
                    seasonNumber = response.seasonNumber,
                    airDate = response.airDate?.trim()?.ifBlank { null },
                    posterPath = response.posterPath?.trim()?.ifBlank { null },
                    episodes =
                        response.episodes.map { episode ->
                            TmdbShowSeasonEpisode(
                                tmdbId = episode.id?.toString(),
                                title = episode.name?.trim()?.ifBlank { null },
                                overview = episode.overview?.trim()?.ifBlank { null },
                                episodeNumber = episode.episodeNumber,
                                airDate = episode.airDate?.trim()?.ifBlank { null },
                                runtimeMinutes = episode.runtime?.takeIf { it > 0 },
                            )
                        },
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
                logger.warn(
                    "Failed to fetch TMDb show season details for id={} season={} status={}",
                    tmdbId,
                    seasonNumber,
                    ex.statusCode.value(),
                    ex,
                )
                return null
            } catch (ex: Exception) {
                logger.warn("Failed to fetch TMDb show season details for id={} season={}", tmdbId, seasonNumber, ex)
                return null
            }
        }

        return null
    }

    fun searchMovies(
        query: String,
        limit: Int = 8,
    ): List<TmdbMovieSearchItem> {
        if (!tmdbProperties.enabled) return emptyList()

        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return emptyList()

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
                            val builder =
                                uriBuilder
                                    .path("/search/movie")
                                    .queryParam("query", normalizedQuery)
                            if (tmdbProperties.token.isBlank() && tmdbProperties.apiKey.isNotBlank()) {
                                builder.queryParam("api_key", tmdbProperties.apiKey)
                            }
                            builder.build()
                        }.retrieve()
                        .bodyToMono<TmdbMovieSearchResponse>()
                        .block() ?: return emptyList()

                return response.results
                    .asSequence()
                    .map { item ->
                        TmdbMovieSearchItem(
                            tmdbId = item.id.toString(),
                            title = item.title?.trim()?.ifBlank { null },
                            originalTitle = item.originalTitle?.trim()?.ifBlank { null },
                            overview = item.overview?.trim()?.ifBlank { null },
                            releaseYear = parseReleaseYear(item.releaseDate),
                            posterPath = item.posterPath?.trim()?.ifBlank { null },
                        )
                    }.filter { !it.title.isNullOrBlank() }
                    .take(limit.coerceAtLeast(1))
                    .toList()
            } catch (ex: WebClientResponseException.NotFound) {
                return emptyList()
            } catch (ex: WebClientResponseException) {
                val isTooManyRequests = ex.statusCode.value() == 429
                val hasRemainingRetry = attempt < attempts
                if (isTooManyRequests && hasRemainingRetry) {
                    val waitMs = resolve429WaitMs(ex, attempt)
                    logger.warn("TMDb rate limited (429). Waiting {}ms before retry {}/{}", waitMs, attempt + 1, attempts)
                    Thread.sleep(waitMs)
                    continue
                }
                logger.warn("Failed to search TMDb movies for query='{}' status={}", normalizedQuery, ex.statusCode.value(), ex)
                return emptyList()
            } catch (ex: Exception) {
                logger.warn("Failed to search TMDb movies for query='{}'", normalizedQuery, ex)
                return emptyList()
            }
        }

        return emptyList()
    }

    fun searchShows(
        query: String,
        limit: Int = 8,
    ): List<TmdbShowSearchItem> {
        if (!tmdbProperties.enabled) return emptyList()

        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return emptyList()

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
                            val builder =
                                uriBuilder
                                    .path("/search/tv")
                                    .queryParam("query", normalizedQuery)
                            if (tmdbProperties.token.isBlank() && tmdbProperties.apiKey.isNotBlank()) {
                                builder.queryParam("api_key", tmdbProperties.apiKey)
                            }
                            builder.build()
                        }.retrieve()
                        .bodyToMono<TmdbShowSearchResponse>()
                        .block() ?: return emptyList()

                return response.results
                    .asSequence()
                    .map { item ->
                        TmdbShowSearchItem(
                            tmdbId = item.id.toString(),
                            title = item.name?.trim()?.ifBlank { null },
                            originalTitle = item.originalName?.trim()?.ifBlank { null },
                            overview = item.overview?.trim()?.ifBlank { null },
                            firstAirYear = parseReleaseYear(item.firstAirDate),
                            posterPath = item.posterPath?.trim()?.ifBlank { null },
                        )
                    }.filter { !it.title.isNullOrBlank() }
                    .take(limit.coerceAtLeast(1))
                    .toList()
            } catch (ex: WebClientResponseException.NotFound) {
                return emptyList()
            } catch (ex: WebClientResponseException) {
                val isTooManyRequests = ex.statusCode.value() == 429
                val hasRemainingRetry = attempt < attempts
                if (isTooManyRequests && hasRemainingRetry) {
                    val waitMs = resolve429WaitMs(ex, attempt)
                    logger.warn("TMDb rate limited (429). Waiting {}ms before retry {}/{}", waitMs, attempt + 1, attempts)
                    Thread.sleep(waitMs)
                    continue
                }
                logger.warn("Failed to search TMDb shows for query='{}' status={}", normalizedQuery, ex.statusCode.value(), ex)
                return emptyList()
            } catch (ex: Exception) {
                logger.warn("Failed to search TMDb shows for query='{}'", normalizedQuery, ex)
                return emptyList()
            }
        }

        return emptyList()
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
