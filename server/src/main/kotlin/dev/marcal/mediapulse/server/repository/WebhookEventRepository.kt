package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.model.WebhookEvent
import org.springframework.data.jpa.repository.JpaRepository

interface WebhookEventRepository : JpaRepository<WebhookEvent, Long>
