package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.comments.CreateMediaCommentRequest
import dev.marcal.mediapulse.server.api.comments.UpdateMediaCommentRequest
import dev.marcal.mediapulse.server.api.magazine.MagazineIssueRequest
import dev.marcal.mediapulse.server.api.magazine.MagazinePublicationRequest
import dev.marcal.mediapulse.server.api.magazine.MagazineReadRequest
import dev.marcal.mediapulse.server.model.magazine.MagazineReadStatus
import dev.marcal.mediapulse.server.repository.crud.MagazineIssueRepository
import dev.marcal.mediapulse.server.repository.crud.MagazinePublicationRepository
import dev.marcal.mediapulse.server.repository.crud.MagazineReadRepository
import dev.marcal.mediapulse.server.repository.crud.MediaCommentRepository
import dev.marcal.mediapulse.server.repository.query.MagazineQueryRepository
import dev.marcal.mediapulse.server.service.comment.MediaCommentService
import dev.marcal.mediapulse.server.service.magazine.MagazineCoverService
import dev.marcal.mediapulse.server.service.magazine.MagazineService
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.mockk
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MagazineIntegrationTest {
    private lateinit var factory: LocalContainerEntityManagerFactoryBean
    private lateinit var service: MagazineService
    private lateinit var comments: MediaCommentService
    private lateinit var tx: TxUtil
    private lateinit var jdbc: JdbcTemplate
    private val day = LocalDate.of(2026, 10, 1)

    @BeforeAll
    fun setup() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        factory =
            LocalContainerEntityManagerFactoryBean().apply {
                dataSource = source
                setPackagesToScan("dev.marcal.mediapulse.server.model.magazine", "dev.marcal.mediapulse.server.model.comment")
                jpaVendorAdapter = HibernateJpaVendorAdapter()
                setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "validate"))
                afterPropertiesSet()
            }
        val emf = factory.`object`!!
        val repositoryFactory = JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf))
        val publications = repositoryFactory.getRepository(MagazinePublicationRepository::class.java)
        val issues = repositoryFactory.getRepository(MagazineIssueRepository::class.java)
        val reads = repositoryFactory.getRepository(MagazineReadRepository::class.java)
        val commentRepo = repositoryFactory.getRepository(MediaCommentRepository::class.java)
        tx = TxUtil(JpaTransactionManager(emf))
        jdbc = JdbcTemplate(source)
        service =
            MagazineService(
                publications,
                issues,
                reads,
                MagazineQueryRepository(NamedParameterJdbcTemplate(source)),
                MediaCommentQueryRepository(commentRepo),
                commentRepo,
                mockk<MagazineCoverService>(relaxed = true),
                tx,
            )
        comments = MediaCommentService(commentRepo, mockk(), mockk(), mockk(), mockk(), mockk(), issues)
    }

    @BeforeEach
    fun clear() {
        jdbc.execute("TRUNCATE magazine_publications, magazine_issues, magazine_reads, media_comments RESTART IDENTITY CASCADE")
    }

    @AfterAll
    fun close() {
        factory.destroy()
    }

    @Test
    fun `sessions comments filtering pagination corrections and deletions survive persistence`() {
        val first =
            service.create(
                MagazineIssueRequest(
                    publication = MagazinePublicationRequest("  Ciência Hoje ", "0101-8515"),
                    number = "100",
                    totalPages = 80,
                    read = MagazineReadRequest(MagazineReadStatus.WANT_TO_READ),
                ),
                null,
            )
        val id = first.issue.id
        val readId = first.reads.single().id
        assertEquals("Ciência Hoje", first.issue.publication.name)
        assertEquals("0101-8515", first.issue.publication.issn)
        service.updateRead(id, readId, MagazineReadRequest(MagazineReadStatus.CURRENTLY_READING, startedAt = day, currentPage = 20))
        assertEquals(
            25.0,
            service
                .details(id)
                .reads
                .single()
                .progressPct,
        )
        assertEquals(
            409,
            assertFailsWith<ResponseStatusException> {
                service.createRead(id, MagazineReadRequest(MagazineReadStatus.WANT_TO_READ))
            }.statusCode.value(),
        )
        val complete = service.updateRead(id, readId, MagazineReadRequest(MagazineReadStatus.READ, finishedAt = day.plusDays(2)))
        assertEquals(day, complete.reads.single().startedAt)
        val comment = tx.inTx { comments.create("magazines", id, CreateMediaCommentRequest("Uma descoberta.")) }
        tx.inTx { comments.update(comment.id, UpdateMediaCommentRequest("Voltei a este número.")) }
        val reread =
            service.createRead(
                id,
                MagazineReadRequest(MagazineReadStatus.CURRENTLY_READING, startedAt = day.plusDays(4), progressPct = 40.0),
            )
        assertEquals(2, reread.reads.size)
        assertNotEquals(readId, reread.reads.first().id)
        assertEquals("Voltei a este número.", reread.comments.single().body)
        assertEquals(
            id,
            service
                .library("ciência", first.issue.publication.id, MagazineReadStatus.CURRENTLY_READING, 0, 1)
                .items
                .single()
                .id,
        )
        assertTrue(service.library("not found", null, null, 0, 24).items.isEmpty())
        assertTrue(service.library(null, null, MagazineReadStatus.READ, 0, 24).items.isEmpty())
        val second =
            service.create(
                MagazineIssueRequest(
                    publicationId = first.issue.publication.id,
                    coverDate = "2026-10",
                    read = MagazineReadRequest(MagazineReadStatus.READ, finishedAt = day.plusDays(5)),
                ),
                null,
            )
        assertEquals(second.reads.single().startedAt, second.reads.single().finishedAt)
        assertEquals(
            second.issue.id,
            service
                .library("10/2026", null, null, 0, 24)
                .items
                .single()
                .id,
        )
        assertEquals(2, service.library(null, null, null, 0, 1).nextPage?.let { service.library(null, null, null, it, 1).items.size + 1 })
        assertEquals(2L, service.overview().completedReadsCount)
        service.updatePublication(first.issue.publication.id, MagazinePublicationRequest("Ciência Hoje revisada", "0101-8515"))
        assertEquals(
            "Ciência Hoje revisada",
            service
                .details(second.issue.id)
                .issue.publication.name,
        )
        service.updateIssue(id, MagazineIssueRequest(publicationId = first.issue.publication.id, number = "100", totalPages = 100), null)
        assertEquals(100, service.details(id).issue.totalPages)
        service.deleteRead(id, reread.reads.first().id)
        assertEquals(1, service.details(id).reads.size)
        assertEquals(1, service.details(id).comments.size)
        tx.inTx {
            comments.delete(comment.id)
            Unit
        }
        assertTrue(service.details(id).comments.isEmpty())
        tx.inTx { comments.create("magazines", id, CreateMediaCommentRequest("Será excluído com o número.")) }
        service.deleteIssue(id)
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM magazine_reads WHERE issue_id = ?", Long::class.java, id))
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM media_comments", Long::class.java))
        assertEquals(404, assertFailsWith<ResponseStatusException> { service.details(id) }.statusCode.value())
    }

    @Test
    fun `invalid initial read rolls back publication and number and duplicate identities conflict`() {
        assertFailsWith<ResponseStatusException> {
            service.create(
                MagazineIssueRequest(
                    publication = MagazinePublicationRequest("Inválida"),
                    number = "1",
                    read = MagazineReadRequest(MagazineReadStatus.READ),
                ),
                null,
            )
        }
        assertEquals(0L, service.overview().numbersCount)
        assertTrue(service.publications().isEmpty())
        val first =
            service.create(
                MagazineIssueRequest(
                    publication = MagazinePublicationRequest("Revista"),
                    number = "1",
                    read = MagazineReadRequest(MagazineReadStatus.WANT_TO_READ),
                ),
                null,
            )
        assertEquals(
            409,
            assertFailsWith<ResponseStatusException> {
                service.create(
                    MagazineIssueRequest(
                        publicationId = first.issue.publication.id,
                        number = "1",
                        coverDate = "2026-10",
                        read = MagazineReadRequest(MagazineReadStatus.WANT_TO_READ),
                    ),
                    null,
                )
            }.statusCode.value(),
        )
        assertEquals(1L, service.overview().numbersCount)
        assertFailsWith<org.springframework.dao.DataIntegrityViolationException> {
            jdbc.update("INSERT INTO magazine_reads(issue_id, status) VALUES (?, 'WANT_TO_READ')", first.issue.id)
        }
    }

    @Test
    fun `concurrent reread starts create only one open journey`() {
        val first =
            service.create(
                MagazineIssueRequest(
                    publication = MagazinePublicationRequest("Revista"),
                    number = "1",
                    read = MagazineReadRequest(MagazineReadStatus.READ, finishedAt = day),
                ),
                null,
            )
        val pool = Executors.newFixedThreadPool(2)
        val gate = CountDownLatch(1)
        try {
            val futures =
                (1..2).map {
                    pool.submit<Int> {
                        gate.await()
                        try {
                            service.createRead(
                                first.issue.id,
                                MagazineReadRequest(MagazineReadStatus.CURRENTLY_READING, startedAt = day.plusDays(1)),
                            )
                            201
                        } catch (
                            error: ResponseStatusException,
                        ) {
                            error.statusCode.value()
                        }
                    }
                }
            gate.countDown()
            assertEquals(listOf(201, 409), futures.map { it.get(15, TimeUnit.SECONDS) }.sorted())
            assertEquals(2, service.details(first.issue.id).reads.size)
        } finally {
            pool.shutdownNow()
        }
    }

    companion object {
        @Container @JvmField
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }
}
