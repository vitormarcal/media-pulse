package dev.marcal.mediapulse.server

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
abstract class MediapulseServerApplicationTests {
    @Test
    fun contextLoads() {
    }
}
