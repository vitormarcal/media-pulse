package dev.marcal.mediapulse.server.integration.tmdb

import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

class TmdbRateLimiterTest {
    @Test
    fun `deve impor intervalo minimo entre requisicoes`() {
        val limiter = TmdbRateLimiter()

        val elapsed =
            measureTimeMillis {
                limiter.acquire(rateLimitPerSecond = 10)
                limiter.acquire(rateLimitPerSecond = 10)
            }

        assertTrue(elapsed >= 90)
    }
}
