package dev.marcal.mediapulse.server.api.movies

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MovieAutomaticEnrichmentDtoTest {
    private val objectMapper =
        JsonMapper
            .builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()

    @Test
    fun `should serialize detailed retry state contract`() {
        val attemptedAt = Instant.parse("2026-09-02T10:00:00Z")
        val retryAfter = Instant.parse("2026-09-03T10:00:00Z")
        val dto =
            MovieAutomaticEnrichmentDto(
                status = MovieEnrichmentStatus.RETRY_SCHEDULED,
                tmdbResolution = MovieEnrichmentStepDto(MovieEnrichmentStatus.COMPLETE),
                terms = MovieEnrichmentStepDto(MovieEnrichmentStatus.RETRY_SCHEDULED, attemptedAt, retryAfter),
                credits = MovieEnrichmentStepDto(MovieEnrichmentStatus.COMPLETE),
                companies = MovieEnrichmentStepDto(MovieEnrichmentStatus.BLOCKED),
            )

        val json = objectMapper.readTree(objectMapper.writeValueAsBytes(dto))

        assertEquals("RETRY_SCHEDULED", json["status"].asText())
        assertEquals("COMPLETE", json["tmdbResolution"]["status"].asText())
        assertEquals("RETRY_SCHEDULED", json["terms"]["status"].asText())
        assertEquals(attemptedAt.toString(), json["terms"]["lastAttemptAt"].asText())
        assertEquals(retryAfter.toString(), json["terms"]["retryAfter"].asText())
        assertTrue(json["credits"].has("lastAttemptAt"))
        assertTrue(json["companies"].has("retryAfter"))
    }
}
