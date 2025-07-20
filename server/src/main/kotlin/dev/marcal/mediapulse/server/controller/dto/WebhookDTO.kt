package dev.marcal.mediapulse.server.controller.dto

import org.springframework.web.multipart.MultipartFile

data class WebhookDTO(
    val provider: String,
    val payload: String,
    val file: MultipartFile? = null,
)
