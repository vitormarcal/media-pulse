package dev.marcal.mediapulse.server.controller

import com.fasterxml.jackson.core.type.TypeReference
import dev.marcal.mediapulse.server.MediapulseServerApplicationTests
import dev.marcal.mediapulse.server.config.JacksonConfig
import dev.marcal.mediapulse.server.controller.dto.ApiResult
import dev.marcal.mediapulse.server.controller.playbacksummary.dto.TrackPlaybackSummary
import dev.marcal.mediapulse.server.model.SourceIdentifier
import dev.marcal.mediapulse.server.model.music.MusicSource
import dev.marcal.mediapulse.server.model.music.MusicSourceIdentifier
import dev.marcal.mediapulse.server.model.music.PlaybackSource
import dev.marcal.mediapulse.server.model.music.TrackPlayback
import dev.marcal.mediapulse.server.repository.MusicAggregationRepository
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
    private lateinit var musicAggregationRepository: MusicAggregationRepository

    val objectMapper = JacksonConfig().objectMapper()

    @Nested
    inner class PlaybackSummaryControllerTests {
        @Test
        fun `should return playback summary for default period when no dates are provided`() {
            createPlaybackAt(
                externalId = "test-track-id-1",
                title = "Test Track 1",
                instant = LocalDateTime.now().minusDays(1).toInstant(java.time.ZoneOffset.UTC),
            )
            createPlaybackAt(
                externalId = "test-track-id-2",
                title = "Test Track 2",
                instant = LocalDateTime.now().minusDays(1).toInstant(java.time.ZoneOffset.UTC),
            )
            createPlaybackAt(
                externalId = "test-track-id-2",
                title = "Test Track 2",
                instant = LocalDateTime.now().minusDays(2).toInstant(java.time.ZoneOffset.UTC),
            )
            createPlaybackAt(
                externalId = "test-track-id-3",
                title = "Test Track 3",
                instant = LocalDateTime.now().minusDays(1).toInstant(java.time.ZoneOffset.UTC),
            )
            createPlaybackAt(
                externalId = "test-track-id-3",
                title = "Test Track 3",
                instant = LocalDateTime.now().minusDays(2).toInstant(java.time.ZoneOffset.UTC),
            )
            createPlaybackAt(
                externalId = "test-track-id-3",
                title = "Test Track 3",
                instant = LocalDateTime.now().minusDays(3).toInstant(java.time.ZoneOffset.UTC),
            )

            val response =
                mockMvc
                    .get("/playbacks/top")
                    .andExpect { status { isOk() } }
                    .andReturn()
                    .response
                    .contentAsString
            val apiResult: ApiResult<List<TrackPlaybackSummary>> =
                objectMapper.readValue(
                    response,
                    object : TypeReference<ApiResult<List<TrackPlaybackSummary>>>() {},
                )

            Assertions.assertTrue(apiResult.data.isNotEmpty()) { "Response should not be empty" }
            Assertions.assertTrue(apiResult.data.size == 3) { "Response should contain 3 unique tracks" }

            Assertions.assertTrue(apiResult.data.any { it.playbackCount.toInt() == 1 }) { "Track 1 should have 1 play" }
            Assertions.assertTrue(apiResult.data.any { it.playbackCount.toInt() == 2 }) { "Track 2 should have 2 play" }
            Assertions.assertTrue(apiResult.data.any { it.playbackCount.toInt() == 3 }) { "Track 3 should have 3 play" }
        }

        @Test
        fun `should return playback summary for provided date range`() {
            createPlaybackAt(
                externalId = "test-track-id-1",
                year = 2025,
                instant = LocalDateTime.now().minusDays(1).toInstant(java.time.ZoneOffset.UTC),
            )
            createPlaybackAt(
                externalId = "test-track-id-2",
                year = 2024,
                instant = LocalDateTime.now().minusDays(6).toInstant(java.time.ZoneOffset.UTC),
            )
            createPlaybackAt(
                externalId = "test-track-id-3",
                year = 2023,
                instant = LocalDateTime.now().minusDays(8).toInstant(java.time.ZoneOffset.UTC),
            )

            val response =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .get("/playbacks/top")
                            .param("period", "week")
                            .contentType(MediaType.APPLICATION_JSON),
                    ).andExpect(status().isOk)
                    .andReturn()
                    .response
                    .contentAsString

            val apiResult: ApiResult<List<TrackPlaybackSummary>> =
                objectMapper.readValue(
                    response,
                    object : TypeReference<ApiResult<List<TrackPlaybackSummary>>>() {},
                )

            Assertions.assertTrue(apiResult.data.isNotEmpty()) { "Response should not be empty" }
            Assertions.assertTrue(apiResult.data.size == 2) { "Response should contain 2 unique tracks" }
            Assertions.assertTrue(apiResult.data.all { it.playbackCount.toInt() == 1 }) { "Track 2 and 3 should have 1 play" }
        }

        @Test
        fun `should return empty list when no playbacks in the provided date range`() {
            createPlaybackAt(
                externalId = "test-track-id-1",
                instant = LocalDateTime.now().minusDays(10).toInstant(java.time.ZoneOffset.UTC),
            )
            createPlaybackAt(
                externalId = "test-track-id-2",
                instant = LocalDateTime.now().minusDays(11).toInstant(java.time.ZoneOffset.UTC),
            )
            createPlaybackAt(
                externalId = "test-track-id-3",
                instant = LocalDateTime.now().minusDays(12).toInstant(java.time.ZoneOffset.UTC),
            )

            val response =
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .get("/playbacks/top")
                            .param("period", "week")
                            .contentType(MediaType.APPLICATION_JSON),
                    ).andExpect(status().isOk)
                    .andReturn()
                    .response
                    .contentAsString

            val apiResult: ApiResult<List<TrackPlaybackSummary>> =
                objectMapper.readValue(
                    response,
                    object : TypeReference<ApiResult<List<TrackPlaybackSummary>>>() {},
                )

            Assertions.assertTrue(apiResult.data.isEmpty()) { "Response should be empty" }
        }

        fun createPlaybackAt(
            externalId: String = "test-track-id",
            title: String = "Test Track",
            album: String = "Test Album",
            artist: String = "Test Artist",
            year: Int = 2023,
            instant: Instant,
        ): TrackPlayback {
            val musicSource =
                musicAggregationRepository.findOrCreate(
                    MusicSource(
                        title = title,
                        album = album,
                        artist = artist,
                        year = year,
                    ),
                    listOf(MusicSourceIdentifier(externalType = SourceIdentifier.MUSICBRAINZ, externalId = externalId)),
                )
            val trackPlayback =
                TrackPlayback(
                    musicSourceId = musicSource.id,
                    source = PlaybackSource.PLEX,
                    playedAt = instant,
                )
            return musicAggregationRepository.registerPlayback(trackPlayback)
        }
    }
}
