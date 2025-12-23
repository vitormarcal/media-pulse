package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.spotify.SpotifySyncState
import org.springframework.data.jpa.repository.JpaRepository

interface SpotifySyncStateCrudRepository : JpaRepository<SpotifySyncState, Long>
