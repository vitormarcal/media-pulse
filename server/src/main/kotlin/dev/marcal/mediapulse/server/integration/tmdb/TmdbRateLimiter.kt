package dev.marcal.mediapulse.server.integration.tmdb

import org.springframework.stereotype.Component
import kotlin.math.ceil

@Component
class TmdbRateLimiter {
    private val lock = Any()
    private var lastRequestAtMs: Long = 0

    fun acquire(rateLimitPerSecond: Int) {
        if (rateLimitPerSecond <= 0) return

        val minIntervalMs = ceil(1000.0 / rateLimitPerSecond.toDouble()).toLong()

        synchronized(lock) {
            val now = System.currentTimeMillis()
            val waitMs = minIntervalMs - (now - lastRequestAtMs)
            if (waitMs > 0) {
                Thread.sleep(waitMs)
            }
            lastRequestAtMs = System.currentTimeMillis()
        }
    }
}
