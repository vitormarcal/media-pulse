package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.model.movie.MovieCreditType
import dev.marcal.mediapulse.server.repository.crud.MovieCreditAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCreditsCrudRepository
import dev.marcal.mediapulse.server.repository.crud.ShowCreditAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.ShowCreditsCrudRepository
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AudiovisualCreditPreservationIntegrationTest {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var factory: LocalContainerEntityManagerFactoryBean
    private lateinit var tx: TransactionTemplate
    private lateinit var movies: MovieCreditAssignmentRepository
    private lateinit var shows: ShowCreditAssignmentRepository
    private lateinit var movieSync: MovieCreditsCrudRepository
    private lateinit var showSync: ShowCreditsCrudRepository
    private val legacyWorks = mutableMapOf<String, Pair<Long, Long>>()

    @BeforeAll
    fun setup() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        jdbc = JdbcTemplate(source)
        Flyway
            .configure()
            .dataSource(source)
            .target("84")
            .load()
            .migrate()
        for (kind in listOf("movie", "show")) {
            val curated = work(kind)
            val automatic = work(kind)
            val person = person()
            jdbc.update("UPDATE ${table(kind)} SET credits_curated_at = NOW() WHERE id = ?", curated)
            for (id in listOf(curated, automatic)) {
                jdbc.update("INSERT INTO ${kind}_credits (${kind}_id, person_id, credit_type) VALUES (?, ?, 'CAST')", id, person)
            }
            legacyWorks[kind] = curated to automatic
        }
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        factory =
            LocalContainerEntityManagerFactoryBean().apply {
                dataSource = source
                setPackagesToScan("dev.marcal.mediapulse.server.model.music")
                jpaVendorAdapter = HibernateJpaVendorAdapter()
                setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "validate"))
                afterPropertiesSet()
            }
        val emf = factory.`object`!!
        val em = SharedEntityManagerCreator.createSharedEntityManager(emf)
        tx = TransactionTemplate(JpaTransactionManager(emf))
        movies = MovieCreditAssignmentRepository(jdbc)
        shows = ShowCreditAssignmentRepository(em)
        movieSync = MovieCreditsCrudRepository(em)
        showSync = ShowCreditsCrudRepository(em)
    }

    @ParameterizedTest
    @ValueSource(strings = ["movie", "show"])
    fun `migration protects existing curated works without freezing pending sync`(kind: String) {
        val (curated, automatic) = legacyWorks.getValue(kind)
        assertEquals(
            true,
            jdbc.queryForObject("SELECT manually_selected FROM ${kind}_credits WHERE ${kind}_id = ?", Boolean::class.java, curated),
        )
        assertEquals(
            false,
            jdbc.queryForObject("SELECT manually_selected FROM ${kind}_credits WHERE ${kind}_id = ?", Boolean::class.java, automatic),
        )
        tx.executeWithoutResult {
            val candidates =
                if (kind ==
                    "movie"
                ) {
                    movieSync.findTmdbSyncCandidates(100).map { it.movieId }
                } else {
                    showSync.findTmdbSyncCandidates(100).map { it.showId }
                }
            assertTrue(curated in candidates)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["movie", "show"])
    fun `repeated refresh preserves selected credit and role while replacing automatic credits`(kind: String) {
        val id = work(kind)
        val chosen = person()
        val old = person()
        val fresh = person()
        tx.executeWithoutResult {
            upsert(kind, id, chosen, "My role", true)
            upsert(kind, id, old, "Old role", false)
            repeat(2) {
                if (kind == "movie") {
                    movies.replaceForMovie(
                        id,
                        listOf(
                            MovieCreditAssignmentRepository.UpsertMovieCreditRequest(
                                id,
                                chosen,
                                MovieCreditType.CAST,
                                characterName = "Provider role",
                            ),
                            MovieCreditAssignmentRepository.UpsertMovieCreditRequest(
                                id,
                                fresh,
                                MovieCreditType.CAST,
                                characterName = "New role",
                            ),
                            MovieCreditAssignmentRepository.UpsertMovieCreditRequest(id, chosen, MovieCreditType.CREW, job = "Director"),
                        ),
                    )
                } else {
                    shows.replaceForShow(
                        id,
                        listOf(
                            ShowCreditAssignmentRepository.UpsertShowCreditRequest(
                                id,
                                chosen,
                                MovieCreditType.CAST,
                                characterName = "Provider role",
                            ),
                            ShowCreditAssignmentRepository.UpsertShowCreditRequest(
                                id,
                                fresh,
                                MovieCreditType.CAST,
                                characterName = "New role",
                            ),
                            ShowCreditAssignmentRepository.UpsertShowCreditRequest(id, chosen, MovieCreditType.CREW, job = "Director"),
                        ),
                    )
                }
            }
            assertEquals(3, count(kind, id))
            assertEquals(
                0,
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ${kind}_credits WHERE ${kind}_id = ? AND person_id = ?",
                    Int::class.java,
                    id,
                    old,
                ),
            )
            assertEquals(
                "My role",
                jdbc.queryForObject(
                    "SELECT character_name FROM ${kind}_credits " +
                        "WHERE ${kind}_id = ? AND person_id = ? AND credit_type = 'CAST'",
                    String::class.java,
                    id,
                    chosen,
                ),
            )
            // A later empty response must still preserve a chosen person outside the provider's selection.
            if (kind == "movie") movies.replaceForMovie(id, emptyList()) else shows.replaceForShow(id, emptyList())
            assertEquals(1, count(kind, id))
            // Deleting is still allowed; there is deliberately no exclusion rule on the next refresh.
            val deleted =
                if (kind == "movie") {
                    movies.deleteCategory(id, chosen, "CAST")
                } else {
                    shows.deleteCategory(id, chosen, "CAST")
                }
            assertEquals(1, deleted)
            upsert(kind, id, chosen, "Provider role", false)
            assertEquals(1, count(kind, id))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["movie", "show"])
    fun `explicit selection promotes automatic credit and survives refresh`(kind: String) {
        val id = work(kind)
        val person = person()
        tx.executeWithoutResult {
            upsert(kind, id, person, "Same role", false)
            upsert(kind, id, person, "Same role", true)
            if (kind == "movie") movies.replaceForMovie(id, emptyList()) else shows.replaceForShow(id, emptyList())
            assertEquals(1, count(kind, id))
            assertEquals(
                true,
                jdbc.queryForObject("SELECT manually_selected FROM ${kind}_credits WHERE ${kind}_id = ?", Boolean::class.java, id),
            )
        }
    }

    private fun upsert(
        kind: String,
        id: Long,
        person: Long,
        role: String,
        selected: Boolean,
    ) {
        if (kind ==
            "movie"
        ) {
            movies.upsert(
                MovieCreditAssignmentRepository.UpsertMovieCreditRequest(
                    id,
                    person,
                    MovieCreditType.CAST,
                    characterName = role,
                    manuallySelected = selected,
                ),
            )
        } else {
            shows.upsert(
                ShowCreditAssignmentRepository.UpsertShowCreditRequest(
                    id,
                    person,
                    MovieCreditType.CAST,
                    characterName = role,
                    manuallySelected = selected,
                ),
            )
        }
    }

    private fun table(kind: String) = if (kind == "movie") "movies" else "tv_shows"

    private fun work(kind: String): Long {
        val key =
            java.util.UUID
                .randomUUID()
                .toString()
        return jdbc.queryForObject(
            "INSERT INTO ${table(kind)} (original_title, fingerprint, tmdb_id) VALUES (?, ?, ?) RETURNING id",
            Long::class.java,
            key,
            key,
            key,
        )!!
    }

    private fun person(): Long {
        val key =
            java.util.UUID
                .randomUUID()
                .toString()
        return jdbc.queryForObject(
            "INSERT INTO people (tmdb_id, name, normalized_name, slug) VALUES (?, ?, ?, ?) RETURNING id",
            Long::class.java,
            key,
            key,
            key,
            key,
        )!!
    }

    private fun count(
        kind: String,
        id: Long,
    ) = jdbc.queryForObject("SELECT COUNT(*) FROM ${kind}_credits WHERE ${kind}_id = ?", Int::class.java, id)

    @AfterAll
    fun close() {
        if (::factory.isInitialized) factory.destroy()
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
