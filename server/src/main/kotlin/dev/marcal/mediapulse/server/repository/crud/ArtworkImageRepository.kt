package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.artwork.ArtworkImage
import org.springframework.data.jpa.repository.JpaRepository

interface ArtworkImageRepository : JpaRepository<ArtworkImage, Long> {
    fun findByArtworkIdOrderByIdAsc(artworkId: Long): List<ArtworkImage>
}
