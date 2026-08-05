package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.spotify.SpotifyCredentials
import org.springframework.data.jpa.repository.JpaRepository

interface SpotifyCredentialsCrudRepository : JpaRepository<SpotifyCredentials, Long>
