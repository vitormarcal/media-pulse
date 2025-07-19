package dev.marcal.mediapulse.server

import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
abstract class MediapulseServerApplicationTests {
    @Test
    fun contextLoads() {
    }
}
