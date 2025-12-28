package dev.marcal.mediapulse.server.service.dispatch

interface EventDispatcher {
    val provider: String

    suspend fun dispatch(
        payload: String,
        eventId: Long?,
    ): DispatchResult
}
