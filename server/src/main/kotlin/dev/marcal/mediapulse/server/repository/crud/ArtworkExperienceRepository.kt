package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.artwork.ArtworkExperience
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ArtworkExperienceRepository : JpaRepository<ArtworkExperience, Long> {
    @Query("select e from ArtworkExperience e where e.artworkId = :artworkId order by e.experiencedAt desc nulls last, e.id desc")
    fun findByArtworkIdOrderByExperiencedAtDescIdDesc(artworkId: Long): List<ArtworkExperience>
}
