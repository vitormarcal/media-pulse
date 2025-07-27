package dev.marcal.mediapulse.server.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "event_sources")
data class EventSource(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val provider: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val payload: String,
    @Column(unique = true)
    val fingerprint: String,
    @Enumerated(EnumType.STRING)
    val status: Status,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at")
    val updatedAt: Instant? = null,
    val errorMessage: String? = null,
) {
    enum class Status {
        PENDING,
        FAILED,
        SUCCESS,
    }

    fun markAsSuccess(): EventSource = this.copy(status = Status.SUCCESS, updatedAt = Instant.now())

    fun markAsFailed(error: String): EventSource = this.copy(status = Status.FAILED, errorMessage = error, updatedAt = Instant.now())
}
