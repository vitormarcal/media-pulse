package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.magazine.MagazineIssue
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface MagazineIssueRepository : JpaRepository<MagazineIssue, Long> {
    fun findByPublicationIdAndIdentityKey(
        publicationId: Long,
        identityKey: String,
    ): MagazineIssue?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from MagazineIssue i where i.id = :id")
    fun lockById(id: Long): MagazineIssue?
}
