package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.magazine.MagazineRead
import dev.marcal.mediapulse.server.model.magazine.MagazineReadStatus
import org.springframework.data.jpa.repository.JpaRepository

interface MagazineReadRepository : JpaRepository<MagazineRead, Long> {
    fun findByIssueIdOrderByIdDesc(issueId: Long): List<MagazineRead>

    fun findByIssueIdInOrderByIdDesc(issueIds: Collection<Long>): List<MagazineRead>

    fun countByStatus(status: MagazineReadStatus): Long
}
