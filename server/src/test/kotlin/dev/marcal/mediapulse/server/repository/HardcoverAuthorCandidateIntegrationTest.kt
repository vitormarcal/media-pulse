package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.books.AuthorEnrichmentDto
import dev.marcal.mediapulse.server.config.JacksonConfig
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverAuthor
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverContribution
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverEdition
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverUserBook
import dev.marcal.mediapulse.server.repository.hardcover.HardcoverAuthorCandidateRepository
import dev.marcal.mediapulse.server.util.FingerprintUtil
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers(disabledWithoutDocker = true)
class HardcoverAuthorCandidateIntegrationTest {
    @Test fun `migration protects existing manual profiles and candidate ambiguity hides only automatic context`() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val jdbc = JdbcTemplate(source)
        val mapper = JacksonConfig().objectMapper()
        Flyway
            .configure()
            .dataSource(source)
            .target("80")
            .load()
            .migrate()

        fun author(name: String) =
            jdbc.queryForObject(
                "INSERT INTO authors(name, fingerprint) VALUES (?, ?) RETURNING id",
                Long::class.java,
                name,
                FingerprintUtil.authorFp(name),
            )!!
        val existing = author("Existente")
        val legacy = AuthorEnrichmentDto(80, "Existente", "https://hardcover.app/authors/existente", Instant.EPOCH, biography = "Manual")
        jdbc.update(
            "INSERT INTO book_author_profiles(author_id, hardcover_id, snapshot) VALUES (?, ?, CAST(? AS jsonb))",
            existing,
            80,
            mapper.writeValueAsString(legacy),
        )
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        val profiles = BookAuthorProfileRepository(jdbc, mapper)
        val candidates = HardcoverAuthorCandidateRepository(jdbc)
        val tx = TransactionTemplate(DataSourceTransactionManager(source))
        assertTrue(profiles.isManual(existing))
        assertEquals(legacy, profiles.visible(existing))
        profiles.saveAutomatically(existing, legacy.copy(biography = "Automática"))
        assertEquals(legacy, profiles.find(existing))

        fun observe(
            name: String,
            externalId: Long?,
        ) = tx.execute {
            candidates.observe(
                HardcoverUserBook(
                    1,
                    edition = HardcoverEdition(contributions = listOf(HardcoverContribution(author = HardcoverAuthor(name, externalId)))),
                ),
            )
        }
        val first = author("Pessoa")
        jdbc.update("UPDATE authors SET hardcover_import_id = 42 WHERE id = ?", first)
        assertEquals(setOf(first), observe("Pessoa", 42))
        observe("Pessoa", 42)
        assertEquals(setOf(first), observe("Nome alterado no provedor", 42))
        assertEquals(emptySet(), observe("Pessoa", null))
        assertEquals(42L, candidates.uniqueIdentity(first))
        assertTrue(candidates.claim(first, 42))
        assertFalse(candidates.claim(first, 42))
        jdbc.update(
            "UPDATE hardcover_author_candidates SET last_attempt_at = CURRENT_TIMESTAMP - INTERVAL '2 days' WHERE author_id = ?",
            first,
        )
        assertTrue(candidates.claim(first, 42))
        val automatic = legacy.copy(hardcoverId = 42, externalName = "Pessoa")
        profiles.saveAutomatically(first, automatic)
        assertFalse(profiles.isManual(first))
        assertEquals(automatic, profiles.visible(first))
        assertEquals(emptySet(), observe("Pessoa", 43))
        jdbc.update("INSERT INTO hardcover_author_candidates(author_id, hardcover_id) VALUES (?, 43)", first)
        assertNull(candidates.uniqueIdentity(first))
        assertNull(profiles.visible(first))
        assertEquals(automatic, profiles.find(first))
        profiles.save(first, automatic.copy(biography = "Confirmada"))
        assertTrue(profiles.isManual(first))
        assertEquals("Confirmada", profiles.visible(first)?.biography)
        profiles.saveAutomatically(first, automatic)
        assertEquals("Confirmada", profiles.find(first)?.biography)
        val second = author("Outro nome")
        jdbc.update("INSERT INTO hardcover_author_candidates(author_id, hardcover_id) VALUES (?, 42)", second)
        assertNull(candidates.uniqueIdentity(second))
        val missing = author("Sem identidade")
        observe("Sem identidade", null)
        observe("Sem identidade", 77)
        assertNull(candidates.uniqueIdentity(missing))
        assertEquals(emptySet(), observe("Autor não cadastrado", 999))
    }

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
