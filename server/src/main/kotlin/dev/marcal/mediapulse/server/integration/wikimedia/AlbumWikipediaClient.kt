package dev.marcal.mediapulse.server.integration.wikimedia

import dev.marcal.mediapulse.server.api.music.AlbumIntroductionDto
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbUrlRelation
import org.springframework.stereotype.Component
import java.net.URI

@Component
class AlbumWikipediaClient(
    private val introductions: WikipediaIntroductionClient,
) {
    suspend fun introduction(relations: List<MbUrlRelation>): AlbumIntroductionDto? = introductions.introduction(relations)

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

    private fun safeUri(value: String?): URI? =
        runCatching { URI(value ?: return null) }.getOrNull()?.takeIf {
            it.scheme in setOf("https", "http") && it.host != null && it.userInfo == null && it.port in setOf(-1, 443, 80)
        }
}
