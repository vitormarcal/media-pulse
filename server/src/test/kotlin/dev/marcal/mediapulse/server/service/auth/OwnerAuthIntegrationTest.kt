package dev.marcal.mediapulse.server.service.auth

import dev.marcal.mediapulse.server.config.OwnerAccessFilter
import dev.marcal.mediapulse.server.config.OwnerAuthProperties
import dev.marcal.mediapulse.server.controller.auth.OwnerAuthController
import dev.marcal.mediapulse.server.repository.OwnerAuthRepository
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class OwnerAuthIntegrationTest {
    @Test
    fun `single use reset persists across restarts and revokes sessions`() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        val jdbc = JdbcTemplate(source)
        val repo = OwnerAuthRepository(jdbc)
        val token = UUID.randomUUID().toString()
        val props = OwnerAuthProperties(token, "webhook-secret")
        val auth = OwnerAuthService(repo, props)
        val tx = TransactionTemplate(DataSourceTransactionManager(source))
        val password = "initial-owner-password"
        assertFailsWith<ResponseStatusException> { auth.login(password) }
        assertFailsWith<ResponseStatusException> { tx.execute { auth.reset("wrong", password) } }
        assertFailsWith<ResponseStatusException> { tx.execute { auth.reset(token, "short") } }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results =
                executor
                    .invokeAll(
                        (1..2).map {
                            Callable { runCatching { tx.execute { auth.reset(token, password) } }.isSuccess }
                        },
                    ).map { it.get() }
            assertEquals(1, results.count { it })
        } finally {
            executor.shutdownNow()
        }
        assertEquals(1, repo.credential().generation)
        assertNotEquals(password, repo.credential().passwordHash)
        val restarted = OwnerAuthService(repo, OwnerAuthProperties(token.uppercase(), "webhook-secret"))
        assertFailsWith<ResponseStatusException> { tx.execute { restarted.reset(token.uppercase(), password) } }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM owner_reset_tokens", Int::class.java))
        assertEquals(1, restarted.login(password))
        val session =
            MockHttpSession().apply {
                setAttribute("ownerGeneration", 1L)
                setAttribute("csrf", "csrf-token")
            }
        val filter = OwnerAccessFilter(restarted, props)

        fun allowed(
            path: String,
            method: String = "GET",
            owner: MockHttpSession? = null,
            csrf: String? = null,
            secret: String? = null,
        ): Int {
            val request =
                MockHttpServletRequest(method, path).apply {
                    servletPath = path
                    if (owner != null) setSession(owner)
                    if (csrf != null) addHeader("X-CSRF-Token", csrf)
                    if (secret != null) addParameter("secret", secret)
                }
            val response = MockHttpServletResponse()
            var reached = false
            filter.doFilter(request, response) { _, _ -> reached = true }
            return if (reached) 200 else response.status
        }
        for (path in listOf(
            "/api/music/lists",
            "/api/books/series",
            "/api/magazines",
            "/api/artworks",
            "/api/movies/lists",
            "/api/shows/lists",
            "/api/games/42/sessions",
            "/api/workouts",
            "/api/people/42/favorite",
            "/api/ratings/movies/42",
            "/api/comments/42",
        )) {
            assertEquals(200, allowed(path), "Public read: $path")
            for (method in listOf("POST", "PUT", "PATCH", "DELETE")) {
                assertEquals(401, allowed(path, method), "Anonymous mutation: $method $path")
                assertEquals(403, allowed(path, method, session), "Missing CSRF: $method $path")
                assertEquals(200, allowed(path, method, session, "csrf-token"), "Owner mutation: $method $path")
            }
        }
        assertEquals(200, allowed("/api/books/library"))
        assertEquals(401, allowed("/api/admin/spotify/status"))
        assertEquals(401, allowed("//api//admin/spotify/status"))
        assertEquals(401, allowed("/oauth/spotify/login"))
        assertEquals(200, allowed("/oauth/spotify/callback"))
        assertEquals(401, allowed("/api/books/series", "POST"))
        assertEquals(200, allowed("/api/people/42/filmography/ensure", "POST"))
        assertEquals(200, allowed("/api/people/42/show-filmography/ensure", "POST"))
        assertEquals(401, allowed("/api/people/42/filmography/movies/3/link", "POST"))
        assertEquals(200, allowed("/api/admin/spotify/status", "OPTIONS"))
        assertEquals(403, allowed("/api/books/series", "POST", session))
        assertEquals(200, allowed("/api/books/series", "POST", session, "csrf-token"))
        assertEquals(401, allowed("/webhook/plex", "POST"))
        assertEquals(401, allowed("/webhook/plex", "POST", secret = "wrong"))
        assertEquals(200, allowed("/webhook/plex", "POST", secret = "webhook-secret"))
        val next = OwnerAuthService(repo, OwnerAuthProperties(UUID.randomUUID().toString()))
        // A new authorization changes the generation; old sessions immediately lose access.
        val nextProps = OwnerAuthProperties(UUID.randomUUID().toString())
        val nextAuth = OwnerAuthService(repo, nextProps)
        tx.execute { nextAuth.reset(nextProps.resetToken, "replacement-owner-password") }
        assertEquals(401, allowed("/api/admin/spotify/status", owner = session))
        assertFailsWith<ResponseStatusException> { next.login(password) }
        assertEquals(2, next.login("replacement-owner-password"))
        val withoutReset = OwnerAuthService(repo, OwnerAuthProperties())
        assertFailsWith<ResponseStatusException> { tx.execute { withoutReset.reset(token, password) } }
        assertEquals(2, withoutReset.login("replacement-owner-password"))
    }

    @Test
    fun `http session rotates on login and logout destroys it`() {
        val auth = io.mockk.mockk<OwnerAuthService>()
        io.mockk.every { auth.generation() } returns 1L
        io.mockk.every { auth.login("owner-password", "127.0.0.1") } returns 1L
        val props = OwnerAuthProperties()
        val mvc =
            MockMvcBuilders
                .standaloneSetup(
                    OwnerAuthController(auth),
                ).addFilter<org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder>(OwnerAccessFilter(auth, props), "/*")
                .build()
        val initial = mvc.perform(get("/api/auth/session").servletPath("/api/auth/session")).andExpect(status().isOk).andReturn()
        val session = initial.request.session as MockHttpSession
        val csrf = session.getAttribute("csrf") as String
        val oldId = session.id
        mvc
            .perform(
                post("/api/auth/login")
                    .servletPath("/api/auth/login")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"password":"owner-password"}"""),
            ).andExpect(status().isForbidden)
        mvc
            .perform(
                post("/api/auth/login")
                    .servletPath("/api/auth/login")
                    .session(session)
                    .header("X-CSRF-Token", csrf)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"password":"owner-password"}"""),
            ).andExpect(status().isOk)
        assertNotEquals(oldId, session.id)
        assertEquals(1L, session.getAttribute("ownerGeneration"))
        mvc
            .perform(post("/api/auth/logout").servletPath("/api/auth/logout").session(session).header("X-CSRF-Token", csrf))
            .andExpect(status().isOk)
        assertTrue(session.isInvalid)
        assertFalse(OwnerAuthService.equal("expected", "other"))
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
