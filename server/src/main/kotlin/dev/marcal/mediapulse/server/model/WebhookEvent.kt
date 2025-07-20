package dev.marcal.mediapulse.server.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "webhook_events")
data class WebhookEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val provider: String,
    @Column(name = "event_timestamp")
    val eventTimestamp: OffsetDateTime = OffsetDateTime.now(),
    @Column(nullable = false, columnDefinition = "TEXT")
    val payload: String,
    @Column(name = "received_at")
    val receivedAt: OffsetDateTime = OffsetDateTime.now(),
    val processed: Boolean = false,
)
