package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.model.WebhookEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WebhookEventRepository : JpaRepository<WebhookEvent, Long>
