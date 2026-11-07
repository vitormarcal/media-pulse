package dev.marcal.mediapulse.server.service.game

import dev.marcal.mediapulse.server.api.games.GameImageCandidateDto
import dev.marcal.mediapulse.server.api.games.GameImageCandidatesResponse
import dev.marcal.mediapulse.server.api.games.GameImageSelectionRequest
import dev.marcal.mediapulse.server.api.games.GameImageSelectionResponse
import dev.marcal.mediapulse.server.integration.igdb.IgdbApiClient
import dev.marcal.mediapulse.server.integration.steamgriddb.SteamGridDbApiClient
import dev.marcal.mediapulse.server.model.game.Game
import dev.marcal.mediapulse.server.repository.crud.GameImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.GameRepository
import dev.marcal.mediapulse.server.service.image.LibraryImageStorageService
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class GameImageSelectionService(
    private val games: GameRepository,
    private val images: GameImageCrudRepository,
    private val igdb: IgdbApiClient,
    private val steam: SteamGridDbApiClient,
    private val storage: LibraryImageStorageService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun candidates(gameId: Long): GameImageCandidatesResponse = candidates(game(gameId))

    private fun candidates(game: Game): GameImageCandidatesResponse {
        val options = mutableListOf<GameImageCandidateDto>()
        val warnings = mutableListOf<String>()
        val steamId =
            try {
                game.steamGridDbId?.toLongOrNull() ?: steam.searchGames(game.title, failOnError = true).firstOrNull()?.id
            } catch (_: Exception) {
                warnings += "Não foi possível buscar o jogo no SteamGridDB. Tente novamente."
                null
            }
        if (steamId != null) {
            for (heroes in listOf(false, true)) {
                try {
                    options +=
                        steam.fetchImageOptions(steamId, heroes).map { image ->
                            val kind =
                                if (heroes) {
                                    "HERO"
                                } else if ((image.width ?: 0) > (image.height ?: 0)) {
                                    "GRID_HORIZONTAL"
                                } else {
                                    "GRID"
                                }
                            candidate("STEAMGRIDDB", kind, image.url, image.thumb ?: image.url, image.width, image.height)
                        }
                } catch (_: Exception) {
                    warnings +=
                        if (heroes) {
                            "Não foi possível buscar os fundos do SteamGridDB."
                        } else {
                            "Não foi possível buscar as capas do SteamGridDB."
                        }
                }
            }
        }
        game.igdbId?.let { id ->
            try {
                igdb.fetchImageOptions(id)?.let { snapshot ->
                    val groups =
                        listOf(
                            "COVER" to listOfNotNull(snapshot.cover),
                            "SCREENSHOT" to snapshot.screenshots,
                            "ARTWORK" to snapshot.artworks,
                        )
                    for ((kind, entries) in groups) {
                        options +=
                            entries.mapNotNull { image ->
                                image.imageId?.let { imageId ->
                                    val root = "https://images.igdb.com/igdb/image/upload"
                                    candidate(
                                        "IGDB",
                                        kind,
                                        "$root/t_original/$imageId.jpg",
                                        "$root/t_screenshot_med/$imageId.jpg",
                                        image.width,
                                        image.height,
                                    )
                                }
                            }
                    }
                }
            } catch (_: Exception) {
                warnings += "Não foi possível buscar as imagens do IGDB. Tente novamente."
            }
        }
        return GameImageCandidatesResponse(options.distinctBy { it.id }, warnings)
    }

    fun save(
        gameId: Long,
        request: GameImageSelectionRequest,
    ): GameImageSelectionResponse {
        if (request.ids.isEmpty() || request.ids.size > 50 || request.ids.any { !it.matches(Regex("[a-f0-9]{64}")) }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione entre uma e 50 imagens disponíveis.")
        }
        val game = game(gameId)
        // Resolve IDs against fresh provider results; never download a URL supplied by the browser.
        val available = candidates(game).images.associateBy { it.id }
        val saved = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for (id in request.ids.distinct()) {
            val image = available[id]
            if (image == null) {
                failed += id
                continue
            }
            try {
                val content = storage.downloadProviderImage(image.url)
                val url = storage.saveGame(content, gameId, game.title, image.provider, DigestUtils.sha1Hex(image.url).take(12))
                images.insertIgnore(gameId, url, image.kind, false)
                saved += id
            } catch (error: Exception) {
                logger.warn(
                    "Falha ao guardar imagem selecionada. gameId={} provider={} error={}",
                    gameId,
                    image.provider,
                    error.javaClass.simpleName,
                )
                failed += id
            }
        }
        return GameImageSelectionResponse(saved, failed)
    }

    private fun game(id: Long): Game =
        games.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Jogo não encontrado.") }

    private fun candidate(
        provider: String,
        kind: String,
        url: String,
        preview: String,
        width: Int?,
        height: Int?,
    ) = GameImageCandidateDto(DigestUtils.sha256Hex(url), provider, kind, url, preview, width, height)
}
