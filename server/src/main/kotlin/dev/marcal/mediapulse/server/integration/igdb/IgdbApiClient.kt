package dev.marcal.mediapulse.server.integration.igdb

import com.fasterxml.jackson.annotation.JsonProperty
import dev.marcal.mediapulse.server.config.IgdbProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.server.ResponseStatusException
import java.text.Normalizer
import java.time.Instant
import java.util.Locale

data class IgdbGameResponse(
    val id: Long,
    val name: String? = null,
    val slug: String? = null,
    val summary: String? = null,
    val storyline: String? = null,
    @JsonProperty("first_release_date")
    val firstReleaseDate: Long? = null,
    val cover: IgdbCoverResponse? = null,
    val genres: List<IgdbNamedResponse> = emptyList(),
    val platforms: List<IgdbNamedResponse> = emptyList(),
)

data class IgdbCoverResponse(
    val id: Long? = null,
    @JsonProperty("image_id")
    val imageId: String? = null,
    val url: String? = null,
)

data class IgdbNamedResponse(
    val id: Long? = null,
    val name: String? = null,
)

private data class TwitchTokenResponse(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("expires_in")
    val expiresIn: Long? = null,
)

@Component
class IgdbApiClient(
    private val props: IgdbProperties,
    private val igdbWebClient: WebClient,
    @Qualifier("igdbOAuthWebClient")
    private val oauthWebClient: WebClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var cachedToken: CachedToken? = null

    fun searchGames(query: String): List<IgdbGameResponse> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()
        ensureConfigured()
        val escaped = normalized.replace("\\", "\\\\").replace("\"", "\\\"")
        val slug = slugify(normalized)
        val fields = "fields id,name,slug,summary,storyline,first_release_date,cover.image_id,cover.url,genres.name,platforms.name;"
        val slugBody =
            """
            $fields
            where slug = "$slug" & version_parent = null;
            limit 10;
            """.trimIndent()
        val exactNameBody =
            """
            $fields
            where name ~ "$escaped" & version_parent = null;
            limit 10;
            """.trimIndent()
        val searchBody =
            """
            search "$escaped";
            $fields
            where version_parent = null;
            limit 10;
            """.trimIndent()
        return listOf(slugBody, exactNameBody, searchBody)
            .flatMap(::postGames)
            .distinctBy { it.id }
            .sortedWith(compareBy<IgdbGameResponse> { suggestionRank(it, normalized, slug) }.thenBy { it.name ?: "" })
            .take(10)
    }

    fun fetchGame(gameId: String): IgdbGameResponse? {
        val normalized = gameId.trim()
        if (normalized.isBlank()) return null
        ensureConfigured()
        val body =
            """
            fields id,name,slug,summary,storyline,first_release_date,cover.image_id,cover.url,genres.name,platforms.name;
            where id = $normalized;
            limit 1;
            """.trimIndent()
        return postGames(body).firstOrNull()
    }

    fun buildCoverUrl(cover: IgdbCoverResponse?): String? {
        val imageId = cover?.imageId?.trim()?.ifBlank { null }
        if (imageId != null) return "https://images.igdb.com/igdb/image/upload/t_cover_big/$imageId.jpg"
        return cover?.url?.let { if (it.startsWith("//")) "https:$it" else it }
    }

    private fun postGames(body: String): List<IgdbGameResponse> =
        runCatching {
            igdbWebClient
                .post()
                .uri("/games")
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON)
                .header("Client-ID", props.clientId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${accessToken()}")
                .bodyValue(body)
                .retrieve()
                .bodyToMono<Array<IgdbGameResponse>>()
                .block()
                ?.toList()
                .orEmpty()
        }.getOrElse { error ->
            if (error is WebClientResponseException) {
                val responseBody = error.responseBodyAsString.take(500)
                logger.warn("IGDB request failed with status {} body={}", error.statusCode, responseBody)
                throw ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "IGDB respondeu ${error.statusCode.value()}: ${responseBody.ifBlank { error.statusText }}",
                    error,
                )
            }

            logger.warn("IGDB request failed", error)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha ao consultar IGDB: ${error.message}", error)
        }

    private fun accessToken(): String {
        val current = cachedToken
        if (current != null && current.expiresAt.isAfter(Instant.now().plusSeconds(60))) return current.value

        val token =
            oauthWebClient
                .post()
                .uri { builder ->
                    builder
                        .path("/token")
                        .queryParam("client_id", props.clientId)
                        .queryParam("client_secret", props.clientSecret)
                        .queryParam("grant_type", "client_credentials")
                        .build()
                }.retrieve()
                .bodyToMono<TwitchTokenResponse>()
                .block()
                ?: error("IGDB token request returned empty response")

        val expiresAt = Instant.now().plusSeconds((token.expiresIn ?: 3600).coerceAtLeast(120))
        cachedToken = CachedToken(token.accessToken, expiresAt)
        return token.accessToken
    }

    private fun ensureConfigured() {
        if (!props.enabled) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "IGDB está desabilitado")
        }
        if (props.clientId.isBlank() || props.clientSecret.isBlank()) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Credenciais IGDB não configuradas: defina IGDB_CLIENT_ID e IGDB_CLIENT_SECRET",
            )
        }
    }

    private fun suggestionRank(
        game: IgdbGameResponse,
        query: String,
        querySlug: String,
    ): Int {
        val name = game.name.orEmpty()
        val normalizedName = normalize(name)
        val normalizedQuery = normalize(query)
        return when {
            game.slug == querySlug -> 0
            normalizedName == normalizedQuery -> 1
            normalizedName.startsWith("$normalizedQuery ") -> 2
            normalizedName.contains(normalizedQuery) -> 3
            else -> 4
        }
    }

    private fun slugify(value: String): String =
        normalize(value)
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')

    private fun normalize(value: String): String =
        Normalizer
            .normalize(value, Normalizer.Form.NFKD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase(Locale.ROOT)
            .trim()

    private data class CachedToken(
        val value: String,
        val expiresAt: Instant,
    )
}
