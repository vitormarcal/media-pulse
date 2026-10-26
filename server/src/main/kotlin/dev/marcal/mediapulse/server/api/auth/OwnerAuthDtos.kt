package dev.marcal.mediapulse.server.api.auth

data class OwnerLoginRequest(
    val password: String,
)

data class OwnerResetRequest(
    val token: String,
    val password: String,
)

data class OwnerSessionResponse(
    val authenticated: Boolean,
    val csrfToken: String,
)
