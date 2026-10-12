package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.artwork.Artwork
import org.springframework.data.jpa.repository.JpaRepository

interface ArtworkRepository : JpaRepository<Artwork, Long> {
    fun findByArtistId(artistId: Long): List<Artwork>
}
