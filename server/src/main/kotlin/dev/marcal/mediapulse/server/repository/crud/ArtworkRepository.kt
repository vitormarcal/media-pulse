package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.artwork.Artwork
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ArtworkRepository : JpaRepository<Artwork, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Artwork a where a.id = :id")
    fun lockById(
        @Param("id") id: Long,
    ): Artwork?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "update Artwork a set a.movementsSyncPending = :pending, a.movementsSyncMessage = :message where a.id = :id and a.movementsRevision = :revision",
    )
    fun updateMovementSync(
        @Param("id") id: Long,
        @Param("revision") revision: Long,
        @Param("pending") pending: Boolean,
        @Param("message") message: String?,
    ): Int

    fun findByArtistId(artistId: Long): List<Artwork>
}
