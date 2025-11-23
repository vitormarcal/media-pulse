package dev.marcal.mediapulse.server.controller.eventsource.dto

import dev.marcal.mediapulse.server.model.EventSource

data class ReprocessRequest(
    val all: Boolean = false,
    val status: List<EventSource.Status> = emptyList(),
    val fromPage: Int = 0,
    val pageSize: Int = 100,
)
