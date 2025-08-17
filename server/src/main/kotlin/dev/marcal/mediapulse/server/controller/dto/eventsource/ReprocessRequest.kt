package dev.marcal.mediapulse.server.controller.dto.eventsource

import dev.marcal.mediapulse.server.model.EventSource

class ReprocessRequest(
    val all: Boolean = false,
    val status: List<EventSource.Status> = emptyList(),
    val fromPage: Int = 0,
    val pageSize: Int = 100,
)
