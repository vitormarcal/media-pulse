package dev.marcal.mediapulse.server.service.game

import dev.marcal.mediapulse.server.api.games.GameSessionCreateRequest
import dev.marcal.mediapulse.server.api.games.GameSessionCreateResponse
import dev.marcal.mediapulse.server.api.games.GameSessionDto
import dev.marcal.mediapulse.server.api.games.GameSessionStatusDto
import dev.marcal.mediapulse.server.model.game.GameSession
import dev.marcal.mediapulse.server.model.game.GameSessionStatus
import dev.marcal.mediapulse.server.repository.crud.GameRepository
import dev.marcal.mediapulse.server.repository.crud.GameSessionCrudRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class GameSessionService(
    private val gameRepository: GameRepository,
    private val gameSessionCrudRepository: GameSessionCrudRepository,
) {
    @Transactional
    fun create(
        gameId: Long,
        request: GameSessionCreateRequest,
    ): GameSessionCreateResponse {
        if (!gameRepository.existsById(gameId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "jogo não encontrado")
        }
        if (request.endedAt != null && request.endedAt.isBefore(request.startedAt)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "fim da sessão não pode ser anterior ao início")
        }

        val saved =
            gameSessionCrudRepository.save(
                GameSession(
                    gameId = gameId,
                    status = GameSessionStatus.valueOf(request.status.name),
                    startedAt = request.startedAt,
                    endedAt = request.endedAt,
                    notes = request.notes?.trim()?.ifBlank { null },
                    createdAt = Instant.now(),
                ),
            )
        return GameSessionCreateResponse(saved.toDto())
    }

    @Transactional
    fun delete(
        gameId: Long,
        sessionId: Long,
    ) {
        val current =
            gameSessionCrudRepository.findById(sessionId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "sessão não encontrada")
            }
        if (current.gameId != gameId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "sessão não encontrada")
        }
        gameSessionCrudRepository.delete(current)
    }

    private fun GameSession.toDto(): GameSessionDto =
        GameSessionDto(
            sessionId = id,
            status = GameSessionStatusDto.valueOf(status.name),
            startedAt = startedAt,
            endedAt = endedAt,
            notes = notes,
        )
}
