package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.books.ApplyBookSeriesSuggestionRequest
import dev.marcal.mediapulse.server.api.books.BookGroupNameRequest
import dev.marcal.mediapulse.server.api.books.BookSeriesMetadataDto
import dev.marcal.mediapulse.server.api.books.BookSeriesMetadataField
import dev.marcal.mediapulse.server.api.books.BookSeriesProviderAuthorDto
import dev.marcal.mediapulse.server.api.books.BookSeriesSuggestionDto
import dev.marcal.mediapulse.server.model.book.BookGroupKind.SERIES
import dev.marcal.mediapulse.server.model.book.BookGroupKind.UNIVERSES
import dev.marcal.mediapulse.server.service.books.BookSeriesService
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class BookSeriesContextIntegrationTest {
    @Test
    fun `migration preserves archive and context derives authors and applies only selected metadata`() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway
            .configure()
            .dataSource(source)
            .target("72")
            .load()
            .migrate()
        val jdbc = JdbcTemplate(source)
        jdbc.update(
            "INSERT INTO books(id, title, slug, fingerprint, hardcover_id) VALUES (1, 'One', 'one', 'one', 10), (2, 'Two', 'two', 'two', 20)",
        )
        jdbc.update("INSERT INTO book_series(id, name, hardcover_id) VALUES (1, 'Local name', 99)")
        jdbc.update("INSERT INTO book_series_items(series_id, book_id, position) VALUES (1, 2, 1), (1, 1, 2)")
        jdbc.update("INSERT INTO book_universes(id, name) VALUES (1, 'Universe')")
        jdbc.update("INSERT INTO book_universe_series(universe_id, series_id, position) VALUES (1, 1, 1)")
        jdbc.update("INSERT INTO book_reads(book_id, source, source_session_id, status) VALUES (1, 'HARDCOVER', 1, 'READ')")
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        val repo = BookSeriesRepository(jdbc)
        val service = BookSeriesService(repo)
        val tx = TransactionTemplate(DataSourceTransactionManager(source))

        fun <T : Any> inTx(action: () -> T): T = tx.execute { action() }!!
        assertEquals(listOf(2L, 1L), service.details(SERIES, 1).items.map { it.id })
        assertEquals(1, service.details(SERIES, 1).universes.size)
        assertNull(service.details(SERIES, 1).description)
        jdbc.update(
            "INSERT INTO authors(id, name, fingerprint) VALUES (1, 'Author', 'author'), (2, 'Coauthor', 'coauthor'), (3, 'Translator', 'translator'), (4, 'Illustrator', 'illustrator')",
        )
        jdbc.update(
            "INSERT INTO book_authors(book_id, author_id, role) VALUES (1,1,'AUTHOR'), (1,2,'AUTHOR'), (2,1,'AUTHOR'), (1,3,'TRANSLATOR'), (1,4,'ILLUSTRATOR'), (2,2,'TRANSLATOR')",
        )
        jdbc.update("INSERT INTO book_editions(book_id, fingerprint, cover_url) VALUES (1, 'edition', '/covers/one.jpg')")
        assertEquals(listOf(1L, 2L), service.details(SERIES, 1).authors.map { it.id })
        assertEquals(1, repo.forAuthor(2).size)
        assertTrue(repo.forAuthor(3).isEmpty())
        assertTrue(repo.forAuthor(4).isEmpty())
        assertEquals(listOf("/covers/one.jpg"), service.list(SERIES).single().coverUrls)
        assertEquals(listOf("/covers/one.jpg"), service.list(UNIVERSES).single().coverUrls)
        assertEquals(
            "/covers/one.jpg",
            service
                .details(SERIES, 1)
                .items
                .last()
                .coverUrl,
        )
        inTx { service.update(SERIES, 1, BookGroupNameRequest("Curated", "Personal description")) }
        val snapshot =
            BookSeriesSuggestionDto(
                99,
                "Provider name",
                repo.items(SERIES, 1),
                "Provider description",
                BookSeriesMetadataDto("provider-series", BookSeriesProviderAuthorDto(500, "External author"), false, 12, 8),
            )
        inTx { service.applyMetadata(1, snapshot, setOf(BookSeriesMetadataField.COUNTS, BookSeriesMetadataField.PRIMARY_AUTHOR)) }
        inTx {
            service.applyMetadata(1, snapshot.copy(description = "x".repeat(30001)), setOf(BookSeriesMetadataField.COUNTS))
        }
        var detail = service.details(SERIES, 1)
        assertEquals("Curated", detail.name)
        assertEquals("Personal description", detail.description)
        assertEquals(12, detail.hardcover.booksCount)
        assertEquals(500, detail.hardcover.primaryAuthor?.id)
        assertNull(detail.hardcover.isCompleted)
        assertEquals(listOf(1L, 2L), detail.authors.map { it.id })
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM authors", Int::class.java))
        inTx {
            service.applyMetadata(
                1,
                snapshot,
                setOf(
                    BookSeriesMetadataField.DESCRIPTION,
                    BookSeriesMetadataField.PUBLICATION_STATUS,
                    BookSeriesMetadataField.EXTERNAL_SLUG,
                ),
            )
        }
        detail = service.details(SERIES, 1)
        assertEquals("Provider description", detail.description)
        assertEquals(false, detail.hardcover.isCompleted)
        assertEquals("provider-series", detail.hardcover.slug)
        assertEquals(listOf(2L, 1L), detail.items.map { it.id })
        inTx { service.update(SERIES, 1, BookGroupNameRequest("Renamed")) }
        assertEquals("Provider description", service.details(SERIES, 1).description)
        inTx { service.update(SERIES, 1, BookGroupNameRequest("Renamed", "")) }
        assertNull(service.details(SERIES, 1).description)
        assertFailsWith<ResponseStatusException> { inTx { service.applyMetadata(1, snapshot, emptySet()) } }
        val conflict =
            assertFailsWith<ResponseStatusException> {
                inTx { service.apply(snapshot.copy(hardcoverId = 100), ApplyBookSeriesSuggestionRequest(100, 1, "Other", listOf(1))) }
            }
        assertEquals(HttpStatus.CONFLICT, conflict.statusCode)
        inTx { service.apply(snapshot, ApplyBookSeriesSuggestionRequest(99, 1, "Ignored", listOf(1), setOf(BookSeriesMetadataField.NAME))) }
        assertEquals("Provider name", service.details(SERIES, 1).name)
        assertEquals(listOf(2L, 1L), service.details(SERIES, 1).items.map { it.id })
        inTx { service.remove(SERIES, 1, 1) }
        assertTrue(repo.forAuthor(2).isEmpty()) // Its remaining credit is translation only.
        assertEquals(listOf(1L), service.details(SERIES, 1).authors.map { it.id })
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM book_reads", Int::class.java))
        // Explicit IDs above do not advance the generated sequence used by the create contract.
        jdbc.queryForObject("SELECT setval(pg_get_serial_sequence('book_series', 'id'), 1)", Long::class.java)
        val created = inTx { service.create(SERIES, "New series", "Initial description") }
        assertEquals("Initial description", created.description)
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
