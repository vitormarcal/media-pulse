package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.books.ApplyBookSeriesSuggestionRequest
import dev.marcal.mediapulse.server.api.books.BookSeriesSuggestionDto
import dev.marcal.mediapulse.server.model.book.BookGroupKind.SERIES
import dev.marcal.mediapulse.server.model.book.BookGroupKind.UNIVERSES
import dev.marcal.mediapulse.server.service.books.BookSeriesService
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class BookSeriesIntegrationTest {
    @Test
    fun `series and universes preserve independent orders and archive on deletion`() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        val jdbc = JdbcTemplate(source)
        val repo = BookSeriesRepository(jdbc)
        val service = BookSeriesService(repo)
        val tx = TransactionTemplate(DataSourceTransactionManager(source))

        fun <T : Any> inTx(action: () -> T): T = tx.execute { action() }!!

        fun book(title: String): Long =
            jdbc.queryForObject(
                "INSERT INTO books(title, slug, fingerprint, hardcover_id) VALUES (?, ?, ?, ?) RETURNING id",
                Long::class.java,
                title,
                title,
                title,
                title.last().digitToInt() + 100,
            )!!
        val a = book("Book1")
        val b = book("Book2")
        val c = book("Book3")
        jdbc.update(
            "INSERT INTO book_reads(book_id, source, source_session_id, source_container_id, status) VALUES (?, 'HARDCOVER', 1, 1, 'READ')",
            a,
        )
        val first = inTx { service.create(SERIES, "  First   series ") }.id
        val second = inTx { service.create(SERIES, "Second") }.id
        assertEquals("First series", service.details(SERIES, first).name)
        inTx { service.add(SERIES, first, a) }
        inTx { service.add(SERIES, first, b) }
        inTx { service.add(SERIES, first, a) }
        inTx { service.add(SERIES, second, a) }
        inTx { service.add(SERIES, second, b) }
        inTx { service.order(SERIES, first, listOf(b, a)) }
        assertEquals(listOf(b, a), service.details(SERIES, first).items.map { it.id })
        assertEquals(listOf(a, b), service.details(SERIES, second).items.map { it.id })
        assertEquals(2, service.forBook(a).size)
        for (invalid in listOf(listOf(a, a), listOf(a), listOf(a, c))) {
            assertFailsWith<ResponseStatusException> { inTx { service.order(SERIES, first, invalid) } }
        }
        val u1 = inTx { service.create(UNIVERSES, "Universe") }.id
        val u2 = inTx { service.create(UNIVERSES, "Universe") }.id
        for (u in listOf(u1, u2)) {
            inTx { service.add(UNIVERSES, u, first) }
            inTx { service.add(UNIVERSES, u, second) }
            inTx { service.add(UNIVERSES, u, first) }
        }
        inTx { service.order(UNIVERSES, u1, listOf(second, first)) }
        assertEquals(listOf(second, first), service.details(UNIVERSES, u1).items.map { it.id })
        assertEquals(listOf(first, second), service.details(UNIVERSES, u2).items.map { it.id })
        assertEquals(2, service.details(SERIES, first).universes.size)
        val suggestion = BookSeriesSuggestionDto(50, "External", repo.items(SERIES, second) + repo.localBooks(103, 3))
        val request = ApplyBookSeriesSuggestionRequest(50, first, "Ignored", listOf(a, c))
        inTx { service.apply(suggestion, request) }
        inTx { service.apply(suggestion, request) }
        assertEquals(listOf(b, a, c), service.details(SERIES, first).items.map { it.id })
        assertFailsWith<ResponseStatusException> {
            inTx { service.apply(suggestion, request.copy(seriesId = null, bookIds = listOf(999))) }
        }
        assertEquals(2, service.list(SERIES).size)
        inTx { service.rename(SERIES, first, "Renamed") }
        inTx { service.remove(UNIVERSES, u1, first) }
        assertEquals(1, service.details(SERIES, first).universes.size)
        inTx { service.delete(SERIES, first) }
        assertEquals(listOf(second), service.details(UNIVERSES, u2).items.map { it.id })
        inTx { service.delete(UNIVERSES, u2) }
        assertEquals(2, service.details(SERIES, second).items.size)
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM books", Int::class.java))
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM book_reads", Int::class.java))

        // Concurrent additions serialize on the parent row, preserving unique positions.
        val concurrent = inTx { service.create(SERIES, "Concurrent") }.id
        val executor = Executors.newFixedThreadPool(3)
        try {
            val futures = executor.invokeAll(listOf(a, b, c).map { id -> Callable { inTx { service.add(SERIES, concurrent, id) } } })
            futures.forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }
        assertEquals(listOf(1, 2, 3), service.details(SERIES, concurrent).items.map { it.position })
        assertTrue(
            service
                .details(SERIES, concurrent)
                .items
                .map { it.id }
                .containsAll(listOf(a, b, c)),
        )
        assertFailsWith<ResponseStatusException> { inTx { service.create(SERIES, " ") } }
        assertFailsWith<ResponseStatusException> { inTx { service.add(SERIES, concurrent, 999) } }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
