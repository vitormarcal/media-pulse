package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.repository.hardcover.HardcoverAuthorIdentityRepository
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
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@Testcontainers(disabledWithoutDocker = true)
class HardcoverAuthorIdentityIntegrationTest {
    @Test fun `migration promotes only unique evidence and identity resolution preserves local authors through rename and homonyms`() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        val jdbc = JdbcTemplate(source)
        Flyway
            .configure()
            .dataSource(source)
            .target("81")
            .load()
            .migrate()

        fun author(name: String) =
            jdbc.queryForObject(
                "INSERT INTO authors(name, fingerprint) VALUES (?, ?) RETURNING id",
                Long::class.java,
                name,
                FingerprintUtil.authorFp(name),
            )!!

        fun evidence(
            id: Long,
            externalId: Long,
        ) {
            jdbc.update("INSERT INTO hardcover_author_candidates(author_id, hardcover_id) VALUES (?, ?)", id, externalId)
        }
        val original = author("Nome original")
        evidence(original, 42)
        val ambiguous = author("Legado ambíguo")
        evidence(ambiguous, 50)
        evidence(ambiguous, 51)
        val missing = author("Sem evidência")
        val shared1 = author("Nome compartilhado A")
        val shared2 = author("Nome compartilhado B")
        evidence(shared1, 70)
        evidence(shared2, 70)
        val manual = author("Perfil manual divergente")
        evidence(manual, 80)
        jdbc.update("INSERT INTO book_author_profiles(author_id,hardcover_id,snapshot) VALUES (?,81,'{}'::jsonb)", manual)
        val book =
            jdbc.queryForObject(
                "INSERT INTO books(title, fingerprint, slug) VALUES ('Livro', 'livro', 'livro') RETURNING id",
                Long::class.java,
            )!!
        jdbc.update("INSERT INTO book_authors(book_id,author_id,role) VALUES (?,?,'AUTHOR')", book, original)
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        assertEquals(42L, jdbc.queryForObject("SELECT hardcover_import_id FROM authors WHERE id=?", Long::class.java, original))
        for (id in listOf(
            ambiguous,
            missing,
            shared1,
            shared2,
            manual,
        )) {
            assertNull(jdbc.queryForObject("SELECT hardcover_import_id FROM authors WHERE id=?", Long::class.java, id))
        }
        val tx = TransactionTemplate(DataSourceTransactionManager(source))
        val identities = HardcoverAuthorIdentityRepository(jdbc)

        fun resolve(
            name: String,
            externalId: Long,
        ) = tx.execute { identities.resolve(name, externalId) }!!
        assertEquals(original, resolve("Nome alterado no provedor", 42))
        assertEquals("Nome original", jdbc.queryForObject("SELECT name FROM authors WHERE id=?", String::class.java, original))
        assertEquals(original, jdbc.queryForObject("SELECT author_id FROM book_authors WHERE book_id=?", Long::class.java, book))
        val homonym = resolve("Nome original", 43)
        assertNotEquals(original, homonym)
        assertEquals(homonym, resolve("Nome original", 43))
        val unknownIdentity = resolve("Sem evidência", 99)
        assertNotEquals(missing, unknownIdentity)
        assertNull(jdbc.queryForObject("SELECT hardcover_import_id FROM authors WHERE id=?", Long::class.java, missing))
        val manualConfirmed = author("Nome escolhido localmente")
        jdbc.update("INSERT INTO book_author_profiles(author_id,hardcover_id,snapshot) VALUES (?,110,'{}'::jsonb)", manualConfirmed)
        assertEquals(manualConfirmed, resolve("Outro nome no provedor", 110))
        val executor = Executors.newFixedThreadPool(4)
        try {
            val ids = executor.invokeAll((1..8).map { n -> Callable { resolve("Nome concorrente $n", 200) } }).map { it.get() }
            assertEquals(1, ids.toSet().size)
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM authors WHERE hardcover_import_id=200", Int::class.java))
            val sameNames =
                executor
                    .invokeAll(
                        listOf(
                            Callable {
                                resolve("Dois homônimos", 201)
                            },
                            Callable { resolve("Dois homônimos", 202) },
                        ),
                    ).map { it.get() }
            assertNotEquals(sameNames[0], sameNames[1])
        } finally {
            executor.shutdownNow()
        }
    }

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
