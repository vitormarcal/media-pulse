package dev.marcal.mediapulse.server.repository

import io.mockk.mockk
import org.flywaydb.core.Flyway
import org.hibernate.cfg.Configuration
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class MixedListsIntegrationTest {
    @ParameterizedTest
    @CsvSource("movie,movies", "show,tv_shows")
    fun `mixed membership is live deduplicated and preserves manual choices`(
        domain: String,
        table: String,
    ) {
        val ids =
            (1..4).map { n ->
                jdbc.queryForObject(
                    "INSERT INTO $table(original_title, fingerprint, favorite, abandoned) VALUES (?, ?, ?, ?) RETURNING id",
                    Long::class.java,
                    "Title $n",
                    "mixed-$domain-$n",
                    n == 2 || n == 4,
                    n >= 3,
                )!!
            }
        val listId =
            jdbc.queryForObject(
                "INSERT INTO ${domain}_lists(name, normalized_name, slug) VALUES ('Mixed', 'mixed', 'mixed') RETURNING id",
                Long::class.java,
            )!!
        for ((index, id) in ids.take(2).reversed().withIndex()) {
            jdbc.update("INSERT INTO ${domain}_list_items(list_id, ${domain}_id, position) VALUES (?, ?, ?)", listId, id, index + 1)
        }
        val factory =
            Configuration()
                .setProperty("hibernate.connection.url", postgres.jdbcUrl)
                .setProperty("hibernate.connection.username", postgres.username)
                .setProperty("hibernate.connection.password", postgres.password)
                .setProperty("hibernate.hbm2ddl.auto", "none")
                .buildSessionFactory()
        factory.use {
            it.openSession().use { session ->
                val movies = MovieQueryRepository(session, mockk(), mockk())
                val shows = ShowListQueryRepository(session)

                fun assertContents(
                    expected: List<Long>,
                    favorites: Boolean,
                    abandoned: Boolean,
                ) {
                    if (domain == "movie") {
                        val detail = movies.getMovieListDetails("mixed")
                        assertEquals(expected, detail.movies.map { m -> m.movieId })
                        assertEquals(expected.size.toLong(), detail.movieCount)
                        assertEquals(favorites, detail.includeFavorites)
                        assertEquals(abandoned, detail.includeAbandoned)
                        val summary = movies.getMovieListSummary(listId)!!
                        assertEquals(expected.size.toLong(), summary.itemCount)
                        assertEquals(expected.take(3), summary.previewMovies.map { m -> m.movieId })
                        assertEquals(summary, movies.listMovieLists().single())
                    } else {
                        val detail = shows.details("mixed")
                        assertEquals(expected, detail.shows.map { s -> s.showId })
                        assertEquals(expected.size.toLong(), detail.showCount)
                        assertEquals(favorites, detail.includeFavorites)
                        assertEquals(abandoned, detail.includeAbandoned)
                        val summary = shows.summary(listId)!!
                        assertEquals(expected.size.toLong(), summary.itemCount)
                        assertEquals(expected.take(3), summary.previewShows.map { s -> s.showId })
                        assertEquals(summary, shows.listAll().single())
                    }
                }
                assertContents(listOf(ids[1], ids[0]), false, false)
                jdbc.update("UPDATE ${domain}_lists SET include_favorites = true WHERE id = ?", listId)
                assertContents(listOf(ids[1], ids[0], ids[3]), true, false)
                jdbc.update("UPDATE ${domain}_lists SET include_abandoned = true WHERE id = ?", listId)
                assertContents(listOf(ids[1], ids[0], ids[2], ids[3]), true, true)
                jdbc.update("UPDATE $table SET favorite = false, abandoned = false WHERE id IN (?, ?)", ids[1], ids[3])
                assertContents(listOf(ids[1], ids[0], ids[2]), true, true)
                jdbc.update("UPDATE $table SET favorite = true WHERE id = ?", ids[1])
                jdbc.update("DELETE FROM ${domain}_list_items WHERE list_id = ? AND ${domain}_id = ?", listId, ids[1])
                assertContents(listOf(ids[0], ids[1], ids[2]), true, true)
                jdbc.update("UPDATE ${domain}_lists SET include_favorites = false, include_abandoned = false WHERE id = ?", listId)
                assertContents(listOf(ids[0]), false, false)
                val manual =
                    if (domain ==
                        "movie"
                    ) {
                        movies.getMovieListDetails("mixed").manualMovieIds
                    } else {
                        shows.details("mixed").manualShowIds
                    }
                assertEquals(listOf(ids[0]), manual)
                jdbc.update("DELETE FROM ${domain}_list_items WHERE list_id = ?", listId)
                assertContents(emptyList(), false, false)
                jdbc.update("UPDATE ${domain}_lists SET include_abandoned = true WHERE id = ?", listId)
                assertContents(listOf(ids[2]), false, true)
                assertTrue(jdbc.queryForList("SELECT * FROM ${domain}_list_items WHERE list_id = ?", listId).isEmpty())
            }
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
            val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            Flyway
                .configure()
                .dataSource(source)
                .load()
                .migrate()
            jdbc = JdbcTemplate(source)
        }
    }
}
