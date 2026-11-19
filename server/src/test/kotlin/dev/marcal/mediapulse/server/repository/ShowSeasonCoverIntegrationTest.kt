package dev.marcal.mediapulse.server.repository

import io.mockk.every
import io.mockk.mockk
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class ShowSeasonCoverIntegrationTest {
    @Test
    fun `migration replacement and season read contracts preserve series and episodes`() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway
            .configure()
            .dataSource(source)
            .target("83")
            .load()
            .migrate()
        val jdbc = JdbcTemplate(source)
        jdbc.update(
            "INSERT INTO tv_shows(id,original_title,fingerprint,slug,cover_url) VALUES (7,'Series','series','series','series-cover')",
        )
        jdbc.update(
            "INSERT INTO tv_episodes(id,show_id,title,season_number,episode_number,fingerprint) VALUES (1,7,'First',1,1,'first'),(2,7,'Special',0,1,'special')",
        )
        jdbc.update("INSERT INTO tv_season_covers(show_id, season_number, cover_url) VALUES (7,1,'first-cover'),(7,0,'special-cover')")
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        val covers = ShowSeasonCoverRepository(jdbc)
        assertEquals("first-cover", covers.images(7, 1).single().url)
        assertEquals(true, covers.images(7, 1).single().isPrimary)
        covers.save(7, 1, "first-cover")
        covers.save(7, 1, "replacement-cover")
        covers.save(7, 0, "special-cover")
        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM tv_season_covers", Long::class.java))
        val factory =
            LocalContainerEntityManagerFactoryBean().apply {
                dataSource = source
                setPackagesToScan("dev.marcal.mediapulse.server.model")
                jpaVendorAdapter = HibernateJpaVendorAdapter()
                afterPropertiesSet()
            }
        val em = factory.`object`!!.createEntityManager()
        try {
            val comments = mockk<MediaCommentQueryRepository>()
            val ratings = mockk<MediaRatingQueryRepository>()
            every { comments.findByEntity(any(), any()) } returns emptyList()
            every { ratings.findByEntity(any(), any()) } returns null
            every { ratings.findByEntities(any(), any()) } returns emptyMap()
            val query = TvShowQueryRepository(em, comments, ratings)
            val details = query.getShowDetails(7)
            assertEquals("series-cover", details.coverUrl)
            assertEquals("replacement-cover", details.seasons.single { it.seasonNumber == 1 }.coverUrl)
            assertEquals("special-cover", details.seasons.single { it.seasonNumber == 0 }.coverUrl)
            assertEquals("replacement-cover", query.getShowSeasonDetailsBySlug("series", 1).coverUrl)
            assertEquals(2, query.getShowSeasonDetailsBySlug("series", 1).images.size)
            covers.append(7, 1, "alternative")
            covers.append(7, 1, "alternative")
            assertEquals(3, covers.images(7, 1).size)
            assertEquals("replacement-cover", covers.images(7, 1).single { it.isPrimary }.url)
            assertFailsWith<ResponseStatusException> { covers.manage(7, 1, "special-cover", false) }
            assertEquals("alternative", covers.manage(7, 1, "alternative", false))
            assertEquals("replacement-cover", covers.manage(7, 1, "alternative", true))
            assertEquals("first-cover", covers.manage(7, 1, "replacement-cover", true))
            assertNull(covers.manage(7, 1, "first-cover", true))
            assertEquals(emptyList(), covers.images(7, 1))
            assertNull(query.getShowSeasonDetailsBySlug("series", 1).coverUrl)
            covers.append(7, 1, "new-first")
            covers.append(7, 1, "new-second")
            assertEquals("new-first", query.getShowSeasonDetailsBySlug("series", 1).coverUrl)
            assertEquals(2, query.getShowSeasonDetailsBySlug("series", 1).images.size)
            assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM tv_episodes", Long::class.java))
            jdbc.update("DELETE FROM tv_shows WHERE id=7")
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM tv_season_covers", Long::class.java))
        } finally {
            em.close()
            factory.destroy()
        }
    }

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
