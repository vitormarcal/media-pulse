package dev.marcal.mediapulse.server.service.game

import dev.marcal.mediapulse.server.integration.igdb.IgdbApiClient
import dev.marcal.mediapulse.server.integration.igdb.IgdbGameResponse
import dev.marcal.mediapulse.server.integration.steamgriddb.SteamGridDbApiClient
import dev.marcal.mediapulse.server.model.game.Game
import dev.marcal.mediapulse.server.repository.crud.GameImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.GameRepository
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import dev.marcal.mediapulse.server.util.FingerprintUtil
import dev.marcal.mediapulse.server.util.SlugTextUtil
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.ZoneOffset

@Service
class ManualGameCatalogService(
    private val gameRepository: GameRepository,
    private val gameImageCrudRepository: GameImageCrudRepository,
    private val igdbApiClient: IgdbApiClient,
    private val steamGridDbApiClient: SteamGridDbApiClient,
    private val imageStorageService: ImageStorageService,
) {
    data class GameCatalogUpsertRequest(
        val title: String,
        val year: Int? = null,
        val igdbId: String? = null,
    )

    data class GameCatalogResult(
        val game: Game,
        val created: Boolean,
        val coverAssigned: Boolean,
    )

    fun resolveOrCreate(request: GameCatalogUpsertRequest): GameCatalogResult {
        val normalizedTitle = request.title.trim().ifBlank { throw IllegalArgumentException("title deve ser preenchido") }
        val normalizedIgdbId = request.igdbId?.trim()?.ifBlank { null }
        val igdbSnapshot = normalizedIgdbId?.let { igdbApiClient.fetchGame(it) }
        val resolvedTitle = igdbSnapshot?.name ?: normalizedTitle
        val resolvedYear = request.year ?: igdbSnapshot?.releaseYear()
        val resolvedDescription = igdbSnapshot?.summary ?: igdbSnapshot?.storyline
        val slug = resolveSlug(resolvedTitle)

        val existingByIgdb = normalizedIgdbId?.let(gameRepository::findByIgdbId)
        val fingerprint = FingerprintUtil.gameFp(resolvedTitle, resolvedYear)
        val existingByFingerprint = if (existingByIgdb == null) gameRepository.findByFingerprint(fingerprint) else null
        val existingGame = existingByIgdb ?: existingByFingerprint
        val created = existingGame == null

        val game =
            existingGame
                ?: gameRepository.save(
                    Game(
                        title = resolvedTitle,
                        originalTitle = resolvedTitle,
                        year = resolvedYear,
                        description = resolvedDescription,
                        igdbId = normalizedIgdbId,
                        slug = slug,
                        fingerprint = fingerprint,
                    ),
                )

        val gameAfterMetadata = fillMissingMetadata(game, resolvedTitle, resolvedYear, resolvedDescription, slug)
        val gameAfterIgdb = normalizedIgdbId?.let { linkIgdbId(gameAfterMetadata, it) } ?: gameAfterMetadata

        val coverAssigned = maybeAssignImages(gameAfterIgdb, resolvedTitle, igdbSnapshot)
        val refreshed = gameRepository.findById(gameAfterIgdb.id).orElse(gameAfterIgdb)
        return GameCatalogResult(game = refreshed, created = created, coverAssigned = coverAssigned)
    }

    fun buildIgdbCoverUrl(game: IgdbGameResponse): String? = igdbApiClient.buildCoverUrl(game.cover)

    private fun maybeAssignImages(
        game: Game,
        title: String,
        igdbSnapshot: IgdbGameResponse?,
    ): Boolean {
        val savedImages = mutableListOf<String>()

        val steamGame = steamGridDbApiClient.searchGames(title).firstOrNull()
        if (steamGame != null) {
            linkSteamGridDbId(game, steamGame.id.toString())
            steamGridDbApiClient
                .fetchGrids(steamGame.id)
                .take(3)
                .forEach { grid ->
                    val localPath =
                        runCatching {
                            val image = steamGridDbApiClient.downloadImage(grid.url)
                            val fileHint = "${game.title}_${DigestUtils.sha1Hex(grid.url).take(12)}"
                            imageStorageService.saveImageForGame(image, "STEAMGRIDDB", game.id, fileHint)
                        }.getOrNull() ?: return@forEach
                    savedImages += localPath
                    gameImageCrudRepository.insertIgnore(game.id, localPath, "GRID", false)
                }
        }

        if (savedImages.isEmpty()) {
            val coverUrl = igdbSnapshot?.let { igdbApiClient.buildCoverUrl(it.cover) }
            if (coverUrl != null) {
                savedImages += coverUrl
                gameImageCrudRepository.insertIgnore(game.id, coverUrl, "COVER", false)
            }
        }

        if (savedImages.isEmpty() || gameImageCrudRepository.existsByGameIdAndIsPrimaryTrue(game.id)) return false

        val primary = savedImages.first()
        gameImageCrudRepository.lockGameRowForPrimaryUpdate(game.id)
        gameImageCrudRepository.clearPrimaryForGame(game.id)
        gameImageCrudRepository.markPrimaryForGame(game.id, primary)
        gameRepository.save(game.copy(coverUrl = primary, updatedAt = Instant.now()))
        return true
    }

    private fun linkIgdbId(
        game: Game,
        igdbId: String,
    ): Game {
        if (game.igdbId == igdbId) return game
        val existing = gameRepository.findByIgdbId(igdbId)
        if (existing != null && existing.id != game.id) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "IGDB $igdbId já está vinculado a outro jogo")
        }
        return gameRepository.save(game.copy(igdbId = igdbId, updatedAt = Instant.now()))
    }

    private fun linkSteamGridDbId(
        game: Game,
        steamGridDbId: String,
    ): Game {
        if (game.steamGridDbId == steamGridDbId) return game
        val existing = gameRepository.findBySteamGridDbId(steamGridDbId)
        if (existing != null && existing.id != game.id) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "STEAMGRIDDB $steamGridDbId já está vinculado a outro jogo")
        }
        return gameRepository.save(game.copy(steamGridDbId = steamGridDbId, updatedAt = Instant.now()))
    }

    private fun fillMissingMetadata(
        game: Game,
        title: String,
        year: Int?,
        description: String?,
        slug: String?,
    ): Game {
        val updatedTitle = game.title.ifBlank { title }
        val updatedOriginalTitle = game.originalTitle.ifBlank { title }
        val updatedYear = game.year ?: year
        val updatedDescription = game.description ?: description
        val updatedSlug = game.slug ?: slug
        val changed =
            updatedTitle != game.title ||
                updatedOriginalTitle != game.originalTitle ||
                updatedYear != game.year ||
                updatedDescription != game.description ||
                updatedSlug != game.slug

        if (!changed) return game
        return gameRepository.save(
            game.copy(
                title = updatedTitle,
                originalTitle = updatedOriginalTitle,
                year = updatedYear,
                description = updatedDescription,
                slug = updatedSlug,
                updatedAt = Instant.now(),
            ),
        )
    }

    private fun resolveSlug(title: String): String? = SlugTextUtil.normalize(title).replace('_', '-').ifBlank { null }

    private fun IgdbGameResponse.releaseYear(): Int? =
        firstReleaseDate
            ?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).year }
}
