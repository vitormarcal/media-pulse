package dev.marcal.mediapulse.server.controller.dto.eventsource

data class ReprocessCounter(
    val total: Long,
    val pageSize: Int,
    val pages: Long,
    val fromPage: Int,
)
