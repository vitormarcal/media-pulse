package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.artwork.ArtworkList
import org.springframework.data.jpa.repository.JpaRepository

interface ArtworkListRepository : JpaRepository<ArtworkList, Long> {
    fun findAllByOrderByNameAscIdAsc(): List<ArtworkList>
}
