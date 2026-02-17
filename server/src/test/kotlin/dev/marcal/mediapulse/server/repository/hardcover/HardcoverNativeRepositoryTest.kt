package dev.marcal.mediapulse.server.repository.hardcover

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HardcoverNativeRepositoryTest {
    private val em = mockk<EntityManager>()
    private val query = mockk<Query>()
    private lateinit var repository: HardcoverNativeRepository
    private val sqls = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        repository = HardcoverNativeRepository()
        HardcoverNativeRepository::class.java
            .getDeclaredField("em")
            .apply { isAccessible = true }
            .set(repository, em)

        sqls.clear()

        every { em.createNativeQuery(any<String>()) } answers {
            sqls += firstArg<String>()
            query
        }
        every { query.setParameter(any<String>(), any<String>()) } returns query
        every { query.resultList } returns listOf(1L)
        every { query.executeUpdate() } returns 1
    }

    @Test
    fun `ensureBookId should overwrite review fields and guard by sourceUpdatedAt`() {
        val sourceUpdatedAt = Instant.parse("2026-02-17T10:00:00Z")

        repository.ensureBookId(
            title = "Book",
            releaseDate = LocalDate.parse("2026-01-01"),
            description = "desc",
            coverUrl = null,
            rating = BigDecimal("4.0"),
            reviewRaw = null,
            reviewedAt = null,
            sourceUpdatedAt = sourceUpdatedAt,
            fingerprint = "fp-book",
        )

        val updateSql = sqls.firstOrNull { it.contains("UPDATE books") }
        assertNotNull(updateSql)
        assertTrue(updateSql.contains("rating = :rating"))
        assertTrue(updateSql.contains("review_raw = :reviewRaw"))
        assertTrue(updateSql.contains("reviewed_at = :reviewedAt"))
        assertTrue(updateSql.contains("updated_at = COALESCE(:sourceUpdatedAt, updated_at, NOW())"))
        assertTrue(updateSql.contains(":sourceUpdatedAt IS NULL"))
        assertTrue(updateSql.contains("updated_at <= :sourceUpdatedAt"))

        verify(atLeast = 1) { query.setParameter("sourceUpdatedAt", sourceUpdatedAt) }
    }

    @Test
    fun `upsertBookRead should allow null overwrite and guard stale updates`() {
        repository.upsertBookRead(
            bookId = 10L,
            editionId = null,
            source = "HARDCOVER",
            sourceEventId = 777L,
            status = "READ",
            startedAt = null,
            finishedAt = null,
            progressPct = null,
            progressPages = null,
            updatedAt = Instant.parse("2026-02-17T10:00:00Z"),
        )

        val upsertSql = sqls.firstOrNull { it.contains("INSERT INTO book_reads") }
        assertNotNull(upsertSql)
        assertTrue(upsertSql.contains("edition_id = EXCLUDED.edition_id"))
        assertTrue(upsertSql.contains("started_at = EXCLUDED.started_at"))
        assertTrue(upsertSql.contains("finished_at = EXCLUDED.finished_at"))
        assertTrue(upsertSql.contains("progress_pct = EXCLUDED.progress_pct"))
        assertTrue(upsertSql.contains("progress_pages = EXCLUDED.progress_pages"))
        assertTrue(upsertSql.contains("book_reads.updated_at IS NULL"))
        assertTrue(upsertSql.contains("EXCLUDED.updated_at IS NULL"))
        assertTrue(upsertSql.contains("book_reads.updated_at <= EXCLUDED.updated_at"))
    }
}
