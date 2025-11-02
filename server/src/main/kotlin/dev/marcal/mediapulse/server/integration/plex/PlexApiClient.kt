package dev.marcal.mediapulse.server.integration.plex

import dev.marcal.mediapulse.server.integration.plex.dto.PlexAlbum
import dev.marcal.mediapulse.server.integration.plex.dto.PlexArtist
import dev.marcal.mediapulse.server.integration.plex.dto.PlexContainer
import dev.marcal.mediapulse.server.integration.plex.dto.PlexLibrarySection
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class PlexApiClient(
    private val plexWebClient: WebClient
) {
    data class PlexAuth(val baseUrl: String, val token: String)

    private fun uri(auth: PlexAuth, pathAndQuery: String) =
        "${auth.baseUrl.trimEnd('/')}$pathAndQuery${if (pathAndQuery.contains("?")) "&" else "?"}X-Plex-Token=${auth.token}"

    suspend fun listMusicSections(auth: PlexAuth): List<PlexLibrarySection> =
        plexWebClient.get()
            .uri(uri(auth, "/library/sections"))
            .retrieve()
            .bodyToMono(object : org.springframework.core.ParameterizedTypeReference<PlexContainer<PlexLibrarySection>>() {})
            .map { it.mc.Directory.orEmpty().filter { s -> s.type == "artist" } }
            .awaitSingle()

    suspend fun listArtistsPaged(
        auth: PlexAuth,
        sectionKey: String,
        start: Int,
        size: Int
    ): Pair<List<PlexArtist>, Int> {
        val path = "/library/sections/$sectionKey/all?type=8&includeGuids=1" +
                "&X-Plex-Container-Start=$start&X-Plex-Container-Size=$size"
        val resp = plexWebClient.get()
            .uri(uri(auth, path))
            .retrieve()
            .toEntity(object : org.springframework.core.ParameterizedTypeReference<PlexContainer<PlexArtist>>() {})
            .awaitSingle()

        val body = resp.body!!.mc
        val total = resp.headers
            .getFirst("X-Plex-Container-Total-Size")?.toInt()
            ?: body.totalSize ?: body.size ?: 0

        val items = (body.Directory ?: body.Metadata).orEmpty()
        return items to total
    }

    suspend fun listAlbumsByArtistPaged(
        auth: PlexAuth,
        sectionKey: String,
        artistRatingKey: String,
        start: Int,
        size: Int
    ): Pair<List<PlexAlbum>, Int> {
        val path = "/library/sections/$sectionKey/all?type=9&artist.id=$artistRatingKey&includeGuids=1" +
                "&X-Plex-Container-Start=$start&X-Plex-Container-Size=$size"
        val resp = plexWebClient.get()
            .uri(uri(auth, path))
            .retrieve()
            .toEntity(object : org.springframework.core.ParameterizedTypeReference<PlexContainer<PlexAlbum>>() {})
            .awaitSingle()

        val body = resp.body!!.mc
        val total = resp.headers
            .getFirst("X-Plex-Container-Total-Size")?.toInt()
            ?: body.totalSize ?: body.size ?: 0

        val items = (body.Directory ?: body.Metadata).orEmpty()
        return items to total
    }
}
