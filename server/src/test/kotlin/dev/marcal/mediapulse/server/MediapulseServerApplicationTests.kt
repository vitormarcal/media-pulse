package dev.marcal.mediapulse.server

import dev.marcal.mediapulse.server.repository.CanonicalTrackRepository
import dev.marcal.mediapulse.server.repository.EventSourceRepository
import dev.marcal.mediapulse.server.repository.TrackPlaybackRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMockMvc
@Tag("integration")
abstract class MediapulseServerApplicationTests {
    @Test
    fun contextLoads() {
    }

    @Autowired
    lateinit var eventSourceRepository: EventSourceRepository

    @Autowired
    lateinit var trackPlaybackRepository: TrackPlaybackRepository

    @Autowired
    lateinit var canonicalTrackRepository: CanonicalTrackRepository

    @BeforeEach
    fun cleanUp() {
        eventSourceRepository.deleteAll()
        trackPlaybackRepository.deleteAll()
        canonicalTrackRepository.deleteAll()
    }

    companion object {
        val postgres = PostgreSQLContainer<Nothing>("postgres:16.2").apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
