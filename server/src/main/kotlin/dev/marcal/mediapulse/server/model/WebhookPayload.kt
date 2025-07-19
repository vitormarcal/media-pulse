package dev.marcal.mediapulse.server.model

import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.util.UUID

@Entity
data class WebhookPayload(
    @Id
    val id: UUID = UUID.randomUUID(),
    val provider: String,
    val payload: String,
)
