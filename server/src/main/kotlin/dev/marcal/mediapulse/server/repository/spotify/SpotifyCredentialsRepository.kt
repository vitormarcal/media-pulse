package dev.marcal.mediapulse.server.repository.spotify

import dev.marcal.mediapulse.server.model.spotify.SpotifyCredentials
import dev.marcal.mediapulse.server.repository.crud.SpotifyCredentialsCrudRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
class SpotifyCredentialsRepository(
    private val crud: SpotifyCredentialsCrudRepository,
) {
    fun getRefreshToken(): String? = crud.findById(1).orElse(null)?.refreshToken

    @Transactional
    fun saveRefreshToken(refreshToken: String) {
        crud.save(SpotifyCredentials(refreshToken = refreshToken, updatedAt = Instant.now()))
    }
}
