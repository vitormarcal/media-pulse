package dev.marcal.mediapulse.server.controller

import com.fasterxml.jackson.core.type.TypeReference
import dev.marcal.mediapulse.server.MediapulseServerApplicationTests
import dev.marcal.mediapulse.server.config.JacksonConfig
import dev.marcal.mediapulse.server.controller.dto.TrackPlaybackSummary
import dev.marcal.mediapulse.server.model.music.CanonicalTrack
import dev.marcal.mediapulse.server.model.music.PlaybackSource
import dev.marcal.mediapulse.server.model.music.TrackPlayback
import dev.marcal.mediapulse.server.repository.PlaybackAggregationRepository
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDateTime

class PlaybackSummaryControllerIT : MediapulseServerApplicationTests() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var playbackAggregationRepository: PlaybackAggregationRepository

    val objectMapper = JacksonConfig().objectMapper()

    @Nested
    inner class PlaybackSummaryControllerTests {
        @Test
        fun `should return playback summary for default period when no dates are provided`() {
            createPlaybackAt(canonicalId = "test-track-id-1", LocalDateTime.now().minusDays(1).toInstant(java.time.ZoneOffset.UTC))
            createPlaybackAt(canonicalId = "test-track-id-2", LocalDateTime.now().minusDays(1).toInstant(java.time.ZoneOffset.UTC))
            createPlaybackAt(canonicalId = "test-track-id-2", LocalDateTime.now().minusDays(2).toInstant(java.time.ZoneOffset.UTC))
            createPlaybackAt(canonicalId = "test-track-id-3", LocalDateTime.now().minusDays(1).toInstant(java.time.ZoneOffset.UTC))
            createPlaybackAt(canonicalId = "test-track-id-3", LocalDateTime.now().minusDays(2).toInstant(java.time.ZoneOffset.UTC))
            createPlaybackAt(canonicalId = "test-track-id-3", LocalDateTime.now().minusDays(3).toInstant(java.time.ZoneOffset.UTC))

            val response =
                mockMvc
                    .get("/playbacks/summary")
                    .andExpect { status { isOk() } }
                    .andReturn()
                    .response
                    .contentAsString
            val typed: List<TrackPlaybackSummary> =
                objectMapper.readValue(
                    response,
                    object : TypeReference<List<TrackPlaybackSummary>>() {},
                )

            Assertions.assertTrue(typed.isNotEmpty()) { "Response should not be empty" }
            Assertions.assertTrue(typed.size == 3) { "Response should contain 3 unique tracks" }

            Assertions.assertTrue(typed.any { it.playbackCount.toInt() == 1 }) { "Track 1 should have 1 play" }
            Assertions.assertTrue(typed.any { it.playbackCount.toInt() == 2 }) { "Track 2 should have 2 play" }
            Assertions.assertTrue(typed.any { it.playbackCount.toInt() == 3 }) { "Track 3 should have 3 play" }
        }

        @Test
        fun `should return playback summary for provided date range`() {
            createPlaybackAt(canonicalId = "test-track-id-1", LocalDateTime.now().minusDays(1).toInstant(java.time.ZoneOffset.UTC))
            createPlaybackAt(canonicalId = "test-track-id-2", LocalDateTime.now().minusDays(2).toInstant(java.time.ZoneOffset.UTC))
            createPlaybackAt(canonicalId = "test-track-id-3", LocalDateTime.now().minusDays(3).toInstant(java.time.ZoneOffset.UTC))

            val response =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .get("/playbacks/summary")
                            .param("start_date", LocalDateTime.now().minusDays(4).toString() + "Z")
                            .param("end_date", LocalDateTime.now().minusDays(2).toString() + "Z")
                            .contentType(MediaType.APPLICATION_JSON),
                    ).andExpect(status().isOk)
                    .andReturn()
                    .response
                    .contentAsString

            val typed: List<TrackPlaybackSummary> =
                objectMapper.readValue(
                    response,
                    object : TypeReference<List<TrackPlaybackSummary>>() {},
                )

            Assertions.assertTrue(typed.isNotEmpty()) { "Response should not be empty" }
            Assertions.assertTrue(typed.size == 2) { "Response should contain 2 unique tracks" }
            Assertions.assertTrue(typed.all { it.playbackCount.toInt() == 1 }) { "Track 2 and 3 should have 1 play" }
        }

        @Test
        fun `should return empty list when no playbacks in the provided date range`() {
            createPlaybackAt(canonicalId = "test-track-id-1", LocalDateTime.now().minusDays(10).toInstant(java.time.ZoneOffset.UTC))
            createPlaybackAt(canonicalId = "test-track-id-2", LocalDateTime.now().minusDays(11).toInstant(java.time.ZoneOffset.UTC))
            createPlaybackAt(canonicalId = "test-track-id-3", LocalDateTime.now().minusDays(12).toInstant(java.time.ZoneOffset.UTC))

            val response =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .get("/playbacks/summary")
                            .param("start_date", LocalDateTime.now().minusDays(4).toString() + "Z")
                            .param("end_date", LocalDateTime.now().minusDays(2).toString() + "Z")
                            .contentType(MediaType.APPLICATION_JSON),
                    ).andExpect(status().isOk)
                    .andReturn()
                    .response
                    .contentAsString

            val typed: List<TrackPlaybackSummary> =
                objectMapper.readValue(
                    response,
                    object : TypeReference<List<TrackPlaybackSummary>>() {},
                )

            Assertions.assertTrue(typed.isEmpty()) { "Response should be empty" }
        }

        fun createPlaybackAt(
            canonicalId: String = "test-track-id",
            instant: Instant,
        ): TrackPlayback {
            val canonicalTrack =
                playbackAggregationRepository.findOrCreate(
                    CanonicalTrack(
                        canonicalId = canonicalId,
                        canonicalType = "MBID",
                        title = "Test Track",
                        album = "Test Album",
                        artist = "Test Artist",
                        year = 2023,
                    ),
                )
            val trackPlayback =
                TrackPlayback(
                    canonicalTrackId = canonicalTrack.id,
                    source = PlaybackSource.PLEX,
                    playedAt = instant,
                )
            return playbackAggregationRepository.registerPlayback(trackPlayback)
        }
    }
}
