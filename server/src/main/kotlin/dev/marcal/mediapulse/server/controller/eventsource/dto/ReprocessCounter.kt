package dev.marcal.mediapulse.server.controller.eventsource.dto

data class ReprocessCounter(
    val total: Long,
    val pageSize: Int,
    val pages: Long,
    val fromIdExclusive: Long,
)
