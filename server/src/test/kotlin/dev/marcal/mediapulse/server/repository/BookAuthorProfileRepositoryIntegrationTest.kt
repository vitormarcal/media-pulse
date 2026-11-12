package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.books.AuthorEnrichmentDto
import dev.marcal.mediapulse.server.config.JacksonConfig
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class BookAuthorProfileRepositoryIntegrationTest {
    @Test
    fun `migration preserves existing authors and stores unique profile with cascading deletion`() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway
            .configure()
            .dataSource(source)
            .target("79")
            .load()
            .migrate()
        val jdbc = JdbcTemplate(source)
        val id = jdbc.queryForObject("INSERT INTO authors(name, fingerprint) VALUES ('Pessoa', 'pessoa') RETURNING id", Long::class.java)!!
        val other = jdbc.queryForObject("INSERT INTO authors(name, fingerprint) VALUES ('Outra', 'outra') RETURNING id", Long::class.java)!!
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        val repository = BookAuthorProfileRepository(jdbc, JacksonConfig().objectMapper())
        assertTrue(repository.exists(id))
        assertNull(repository.find(id))
        val profile =
            AuthorEnrichmentDto(
                42,
                "Pessoa externa",
                "https://hardcover.app/authors/pessoa",
                Instant.parse("2026-11-12T12:00:00.123456789Z"),
                biography = "Texto com 'aspas' e acentuação.",
                born = "1980",
                photoUrl = "/covers/photo.jpg",
            )
        repository.save(id, profile)
        assertEquals(profile, repository.find(id))
        assertEquals(id, repository.owner(42))
        assertFailsWith<DataIntegrityViolationException> { repository.save(other, profile) }
        repository.save(id, profile.copy(hardcoverId = 99, biography = "Nova"))
        assertNull(repository.owner(42))
        assertEquals(99L, repository.find(id)?.hardcoverId)
        assertEquals("Pessoa", jdbc.queryForObject("SELECT name FROM authors WHERE id = ?", String::class.java, id))
        jdbc.update("DELETE FROM authors WHERE id = ?", id)
        assertNull(repository.find(id))
    }

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
