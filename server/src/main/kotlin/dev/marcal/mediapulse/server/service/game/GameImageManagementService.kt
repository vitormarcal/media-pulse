package dev.marcal.mediapulse.server.service.game

import dev.marcal.mediapulse.server.api.games.GameImageManagementResponse
import dev.marcal.mediapulse.server.repository.crud.GameImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.GameRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class GameImageManagementService(
    private val games: GameRepository,
    private val images: GameImageCrudRepository,
) {
    @Transactional
    fun update(
        gameId: Long,
        url: String,
        remove: Boolean,
    ): GameImageManagementResponse {
        if (images.lockGameRowForPrimaryUpdate(gameId) == null) missing()
        val game = games.findById(gameId).orElseThrow { missing() }
        val gallery = images.findByGameIdOrderByIsPrimaryDescIdDesc(gameId)
        if (url.isBlank() || (game.coverUrl != url && gallery.none { it.url == url })) missing()

        val cover =
            if (remove) {
                images.removeForGame(gameId, url)
                if (game.coverUrl == url || gallery.any { it.url == url && it.isPrimary }) {
                    gallery.firstOrNull { it.url != url }?.url
                } else {
                    game.coverUrl
                }
            } else {
                // Older covers can exist without a gallery association.
                images.insertIgnore(gameId, url, "COVER", false)
                url
            }
        images.clearPrimaryForGame(gameId)
        if (cover != null) images.markPrimaryForGame(gameId, cover)
        games.updateCover(gameId, cover)
        return GameImageManagementResponse(cover)
    }

    private fun missing(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "Jogo ou imagem não encontrado.")
}
