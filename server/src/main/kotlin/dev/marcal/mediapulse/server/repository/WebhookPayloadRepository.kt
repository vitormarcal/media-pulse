package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.model.WebhookPayload
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WebhookPayloadRepository : JpaRepository<WebhookPayload, UUID>
