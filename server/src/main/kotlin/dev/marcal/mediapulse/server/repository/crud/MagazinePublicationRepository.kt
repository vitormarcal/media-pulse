package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.magazine.MagazinePublication
import org.springframework.data.jpa.repository.JpaRepository

interface MagazinePublicationRepository : JpaRepository<MagazinePublication, Long> {
    fun findByNormalizedName(normalizedName: String): MagazinePublication?

    fun findByIssn(issn: String): MagazinePublication?

    fun findAllByOrderByNameAsc(): List<MagazinePublication>
}
