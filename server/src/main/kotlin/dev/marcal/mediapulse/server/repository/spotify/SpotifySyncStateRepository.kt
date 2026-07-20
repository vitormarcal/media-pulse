package dev.marcal.mediapulse.server.repository.spotify

import dev.marcal.mediapulse.server.model.spotify.SpotifyAuthorizationStatus
import dev.marcal.mediapulse.server.model.spotify.SpotifySyncState
import dev.marcal.mediapulse.server.repository.crud.SpotifySyncStateCrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
class SpotifySyncStateRepository(
    private val crud: SpotifySyncStateCrudRepository,
) {
    @Transactional
    fun getOrCreateSingleton(): SpotifySyncState {
        val existing = crud.findAll().firstOrNull()
        return existing ?: crud.save(SpotifySyncState(cursorAfterMs = 0, updatedAt = Instant.now()))
    }

    @Transactional
    fun updateCursor(newAfterMs: Long): SpotifySyncState {
        val current = getOrCreateSingleton()
        if (newAfterMs <= current.cursorAfterMs) return current
        return crud.save(current.copy(cursorAfterMs = newAfterMs, updatedAt = Instant.now()))
    }

    @Transactional
    fun markHealthy(): SpotifySyncState {
        val now = Instant.now()
        return crud.save(
            getOrCreateSingleton().copy(
                authorizationStatus = SpotifyAuthorizationStatus.HEALTHY,
                lastSuccessAt = now,
                lastErrorCode = null,
                updatedAt = now,
            ),
        )
    }

    @Transactional
    fun markFailure(
        status: SpotifyAuthorizationStatus,
        errorCode: String,
    ): SpotifySyncState {
        val now = Instant.now()
        return crud.save(
            getOrCreateSingleton().copy(
                authorizationStatus = status,
                lastFailureAt = now,
                lastErrorCode = errorCode.take(100),
                updatedAt = now,
            ),
        )
    }
}
