package dev.marcal.mediapulse.server.config

import dev.marcal.mediapulse.server.service.auth.OwnerAuthService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
class OwnerAccessFilter(
    private val auth: OwnerAuthService,
    private val props: OwnerAuthProperties,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (request.method == "OPTIONS") {
            chain.doFilter(request, response)
            return
        }
        val path = request.servletPath.replace(Regex("/+"), "/")
        val safe = request.method in setOf("GET", "HEAD", "OPTIONS")
        val ensure = request.method == "POST" && Regex("/api/people/[0-9]+/(show-filmography|filmography)/ensure").matches(path)
        if (path == "/webhook/plex") {
            if (props.plexWebhookSecret.isBlank() ||
                !OwnerAuthService.equal(props.plexWebhookSecret, request.getParameter("secret") ?: "")
            ) {
                response.sendError(401)
                return
            }
        } else {
            val authAction = path in setOf("/api/auth/login", "/api/auth/reset", "/api/auth/logout")
            val protected =
                path == "/api/admin" ||
                    path.startsWith("/api/admin/") ||
                    path == "/oauth/spotify/login" ||
                    (!safe && !ensure && !authAction)
            val session = request.getSession(false)
            if (protected && session?.getAttribute("ownerGeneration") != auth.generation()) {
                response.sendError(401)
                return
            }
            if (!safe && !ensure) {
                val expected = session?.getAttribute("csrf") as String?
                if (expected == null || !OwnerAuthService.equal(expected, request.getHeader("X-CSRF-Token") ?: "")) {
                    response.sendError(403)
                    return
                }
            }
            if (path.startsWith("/api/auth/") || protected) response.setHeader("Cache-Control", "no-store")
        }
        chain.doFilter(request, response)
    }
}
