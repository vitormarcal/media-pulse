package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.controller.movies.MoviePersonalMarksController
import dev.marcal.mediapulse.server.controller.shows.ShowPersonalMarksController
import dev.marcal.mediapulse.server.service.movie.MoviePersonalMarksService
import dev.marcal.mediapulse.server.service.tv.ShowPersonalMarksService
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class PersonalMarksIntegrationTest {
    @ParameterizedTest
    @CsvSource("movies,movies", "shows,tv_shows")
    fun `API persists independent reversible marks without changing metadata`(
        domain: String,
        table: String,
    ) {
        val mvc =
            MockMvcBuilders
                .standaloneSetup(
                    MoviePersonalMarksController(MoviePersonalMarksService(MoviePersonalMarksRepository(jdbc))),
                    ShowPersonalMarksController(ShowPersonalMarksService(ShowPersonalMarksRepository(jdbc))),
                ).build()
        val id =
            jdbc.queryForObject(
                "INSERT INTO $table(original_title, fingerprint) VALUES ('Original', ?) RETURNING id",
                Long::class.java,
                "marks-$domain",
            )!!

        fun assertMarks(
            favorite: Boolean,
            abandoned: Boolean,
        ) {
            val row = jdbc.queryForMap("SELECT favorite, abandoned, original_title FROM $table WHERE id = ?", id)
            assertEquals(favorite, row["favorite"])
            assertEquals(abandoned, row["abandoned"])
            assertEquals("Original", row["original_title"])
        }
        assertMarks(false, false)
        repeat(2) { mvc.perform(post("/api/$domain/$id/favorite")).andExpect(status().isNoContent) }
        assertMarks(true, false)
        repeat(2) { mvc.perform(post("/api/$domain/$id/abandoned")).andExpect(status().isNoContent) }
        assertMarks(true, true)
        jdbc.update("UPDATE $table SET description = 'Provider refresh', updated_at = NOW() WHERE id = ?", id)
        assertMarks(true, true)
        repeat(2) { mvc.perform(delete("/api/$domain/$id/favorite")).andExpect(status().isNoContent) }
        assertMarks(false, true)
        repeat(2) { mvc.perform(delete("/api/$domain/$id/abandoned")).andExpect(status().isNoContent) }
        assertMarks(false, false)
        assertEquals("Provider refresh", jdbc.queryForObject("SELECT description FROM $table WHERE id = ?", String::class.java, id))
        for (mark in listOf("favorite", "abandoned")) {
            mvc.perform(post("/api/$domain/9223372036854775807/$mark")).andExpect(status().isNotFound)
            mvc.perform(delete("/api/$domain/9223372036854775807/$mark")).andExpect(status().isNotFound)
        }
    }

    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:16-alpine")
        private lateinit var jdbc: JdbcTemplate

        @BeforeAll
        @JvmStatic
        fun migrate() {
            val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            Flyway
                .configure()
                .dataSource(dataSource)
                .load()
                .migrate()
            jdbc = JdbcTemplate(dataSource)
        }
    }
}
