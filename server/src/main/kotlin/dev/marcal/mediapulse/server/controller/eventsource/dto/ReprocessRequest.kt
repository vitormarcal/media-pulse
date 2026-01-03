package dev.marcal.mediapulse.server.controller.eventsource.dto

import dev.marcal.mediapulse.server.model.EventSource

data class ReprocessRequest(
    val all: Boolean = false,
    val status: List<EventSource.Status> = listOf(EventSource.Status.PENDING, EventSource.Status.FAILED),
    val providers: List<String> = emptyList(),
    val pageSize: Int = 1000,
    val fromIdExclusive: Long = 0,
)
