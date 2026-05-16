package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.game.GameSession
import org.springframework.data.repository.CrudRepository

interface GameSessionCrudRepository : CrudRepository<GameSession, Long>
