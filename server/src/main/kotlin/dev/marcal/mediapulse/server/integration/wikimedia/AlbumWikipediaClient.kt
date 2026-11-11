package dev.marcal.mediapulse.server.integration.wikimedia

import com.fasterxml.jackson.databind.JsonNode
import dev.marcal.mediapulse.server.api.music.AlbumIntroductionDto
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbUrlRelation
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.Duration

@Component
class AlbumWikipediaClient(
    @Qualifier("wikimediaWebClient") private val client: WebClient,
) {
    private data class Article(
        val language: String,
        val title: String,
    )

    private data class Page(
        val introduction: AlbumIntroductionDto?,
        val portugueseTitle: String?,
    )

    suspend fun introduction(relations: List<MbUrlRelation>): AlbumIntroductionDto? {
        val articles = relations.mapNotNull { article(it.url?.resource) }.associateBy { it.language }.toMutableMap()
        val wikidata =
            relations
                .asSequence()
                .mapNotNull { safeUri(it.url?.resource) }
                .firstOrNull {
                    it.host in setOf("www.wikidata.org", "wikidata.org") &&
                        Regex("/(?:wiki|entity)/Q[1-9][0-9]*").matches(it.path)
                }
        if (wikidata != null) {
            val id = wikidata.path.substringAfterLast('/')
            val response =
                get(
                    "www.wikidata.org",
                    mapOf(
                        "action" to "wbgetentities",
                        "ids" to id,
                        "props" to "sitelinks",
                        "sitefilter" to "ptwiki|enwiki",
                    ),
                )
            val sitelinks = response.path("entities").path(id).path("sitelinks")
            for (language in listOf("pt", "en")) {
                sitelinks.path("${language}wiki").path("title").asText("").takeIf { it.isNotBlank() }?.let {
                    articles[language] =
                        Article(language, it)
                }
            }
        }
        articles["pt"]?.let { page(it).introduction?.let { intro -> return intro } }
        val english = articles["en"] ?: return null
        val page = page(english)
        if (articles["pt"] == null) {
            page.portugueseTitle?.let { page(Article("pt", it)).introduction?.let { intro -> return intro } }
        }
        return page.introduction
    }

    fun externalLinks(
        relations: List<MbUrlRelation>,
        mbid: String,
    ): Map<String, String> =
        buildMap {
            put("MusicBrainz", "https://musicbrainz.org/release-group/$mbid")
            for (relation in relations) {
                val uri = safeUri(relation.url?.resource) ?: continue
                val label =
                    when {
                        uri.host == "open.spotify.com" && uri.path.startsWith("/album/") -> "Spotify"
                        uri.host in setOf("discogs.com", "www.discogs.com") -> "Discogs"
                        uri.host.endsWith(".bandcamp.com") -> "Bandcamp"
                        else -> null
                    }
                if (label != null) putIfAbsent(label, uri.toString().replaceFirst("http:", "https:"))
            }
        }

    private suspend fun page(article: Article): Page {
        require(article.title.isNotBlank() && article.title.length <= 500 && '|' !in article.title) { "Invalid article title" }
        val response =
            get(
                "${article.language}.wikipedia.org",
                mapOf(
                    "action" to "query",
                    "prop" to "extracts|info|revisions|pageprops|langlinks",
                    "titles" to article.title,
                    "exintro" to "1",
                    "explaintext" to "1",
                    "inprop" to "url",
                    "rvprop" to "ids",
                    "ppprop" to "disambiguation",
                    "lllang" to "pt",
                    "redirects" to "1",
                    "meta" to "siteinfo",
                    "siprop" to "rightsinfo",
                ),
            )
        val query = response.path("query")
        val page = query.path("pages").firstOrNull() ?: return Page(null, null)
        if (page.has("missing") || page.path("ns").asInt(-1) != 0 || page.path("pageprops").has("disambiguation")) return Page(null, null)
        val portugueseTitle =
            page
                .path("langlinks")
                .firstOrNull()
                ?.path("title")
                ?.asText()
                ?.takeIf { it.isNotBlank() }
        val text =
            page
                .path("extract")
                .asText("")
                .trim()
                .takeIf { it.isNotBlank() } ?: return Page(null, portugueseTitle)
        val source =
            safeUri(page.path("fullurl").asText())?.takeIf { it.host == "${article.language}.wikipedia.org" }
                ?: error("Wikipedia returned an invalid article URL")
        val rights = query.path("rightsinfo")
        val license = rights.path("text").asText("").takeIf { it.isNotBlank() } ?: error("Missing Wikipedia attribution")
        val licenseUrl = safeUri(rights.path("url").asText())?.toString() ?: error("Missing Wikipedia license URL")
        return Page(
            AlbumIntroductionDto(
                text = text,
                language = article.language,
                sourceUrl = source.toString().replaceFirst("http:", "https:"),
                revisionId =
                    page
                        .path("revisions")
                        .firstOrNull()
                        ?.path("revid")
                        ?.takeIf { it.isIntegralNumber }
                        ?.asLong(),
                license = license,
                licenseUrl = licenseUrl.replaceFirst("http:", "https:"),
            ),
            portugueseTitle,
        )
    }

    private fun article(url: String?): Article? {
        val uri = safeUri(url) ?: return null
        val language =
            when (uri.host) {
                "pt.wikipedia.org" -> "pt"
                "en.wikipedia.org" -> "en"
                else -> return null
            }
        if (!uri.path.startsWith("/wiki/")) return null
        return uri.path
            .removePrefix("/wiki/")
            .takeIf { it.isNotBlank() }
            ?.let { Article(language, it.replace('_', ' ')) }
    }

    private fun safeUri(value: String?): URI? =
        runCatching { URI(value ?: return null) }.getOrNull()?.takeIf {
            it.scheme in setOf("https", "http") && it.host != null && it.userInfo == null && it.port in setOf(-1, 443, 80)
        }

    private suspend fun get(
        host: String,
        params: Map<String, String>,
    ): JsonNode {
        val builder =
            UriComponentsBuilder
                .fromUriString(
                    "https://$host/w/api.php",
                ).queryParam("format", "json")
                .queryParam("formatversion", "2")
        params.forEach { (key, value) -> builder.queryParam(key, value) }
        val response =
            client
                .get()
                .uri(
                    builder.build().encode().toUri(),
                ).retrieve()
                .bodyToMono<JsonNode>()
                .timeout(Duration.ofSeconds(20))
                .awaitSingle()
        check(!response.has("error")) { "Wikimedia API returned an error" }
        return response
    }
}
