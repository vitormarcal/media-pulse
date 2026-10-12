package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.artwork.ArtworkListItem
import org.springframework.data.jpa.repository.JpaRepository

interface ArtworkListItemRepository : JpaRepository<ArtworkListItem, Long> {
    fun findByArtworkId(artworkId: Long): List<ArtworkListItem>

    fun findByListIdAndArtworkId(
        listId: Long,
        artworkId: Long,
    ): ArtworkListItem?

    fun countByListId(listId: Long): Long
}
