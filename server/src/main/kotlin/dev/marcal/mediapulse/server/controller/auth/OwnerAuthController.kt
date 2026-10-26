package dev.marcal.mediapulse.server.controller.auth

import dev.marcal.mediapulse.server.api.auth.OwnerLoginRequest
import dev.marcal.mediapulse.server.api.auth.OwnerResetRequest
import dev.marcal.mediapulse.server.api.auth.OwnerSessionResponse
import dev.marcal.mediapulse.server.service.auth.OwnerAuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/auth")
class OwnerAuthController(
    private val auth: OwnerAuthService,
) {
    @GetMapping("/session")
    fun session(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): OwnerSessionResponse {
        response.setHeader("Cache-Control", "no-store")
        val session = request.getSession(true)
        val token = session.getAttribute("csrf") as String? ?: UUID.randomUUID().toString().also { session.setAttribute("csrf", it) }
        return OwnerSessionResponse(session.getAttribute("ownerGeneration") == auth.generation(), token)
    }

    @PostMapping("/login")
    fun login(
        @RequestBody body: OwnerLoginRequest,
        request: HttpServletRequest,
    ) {
        val generation = auth.login(body.password, request.remoteAddr)
        request.changeSessionId()
        request.session.setAttribute("ownerGeneration", generation)
    }

    @PostMapping("/reset")
    fun reset(
        @RequestBody body: OwnerResetRequest,
        request: HttpServletRequest,
    ) {
        auth.reset(body.token, body.password, request.remoteAddr)
        request.getSession(false)?.invalidate()
    }

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest) {
        request.getSession(false)?.invalidate()
    }
}
