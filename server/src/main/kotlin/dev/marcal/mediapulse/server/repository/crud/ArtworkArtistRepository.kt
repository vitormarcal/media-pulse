package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.artwork.ArtworkArtist
import org.springframework.data.jpa.repository.JpaRepository

interface ArtworkArtistRepository : JpaRepository<ArtworkArtist, Long> {
    fun findAllByOrderByNameAscIdAsc(): List<ArtworkArtist>
}
