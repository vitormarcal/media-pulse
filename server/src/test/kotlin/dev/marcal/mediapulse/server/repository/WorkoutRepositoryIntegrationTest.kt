package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.model.workout.Workout
import dev.marcal.mediapulse.server.model.workout.WorkoutCategory
import dev.marcal.mediapulse.server.repository.crud.WorkoutRepository
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class WorkoutRepositoryIntegrationTest {
    @Test
    fun `migration entity and repository preserve metrics with stable filtered pagination`() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        val factory =
            LocalContainerEntityManagerFactoryBean().apply {
                dataSource = source
                setPackagesToScan("dev.marcal.mediapulse.server.model.workout")
                jpaVendorAdapter = HibernateJpaVendorAdapter()
                setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "validate"))
                afterPropertiesSet()
            }
        val manager = factory.`object`!!.createEntityManager()
        try {
            val repo = JpaRepositoryFactory(manager).getRepository(WorkoutRepository::class.java)
            manager.transaction.begin()
            val started = Instant.parse("2026-09-21T10:30:00Z")
            val run =
                repo.saveAndFlush(
                    Workout(
                        category = WorkoutCategory.RUNNING,
                        startedAt = started,
                        durationMinutes = 32,
                        distanceKm = BigDecimal("5.125"),
                        location = "Parque",
                        photoUrl = "/covers/workouts/remember.jpg",
                    ),
                )
            val rope = repo.saveAndFlush(Workout(category = WorkoutCategory.JUMP_ROPE, startedAt = started, durationMinutes = 15))
            repo.saveAndFlush(Workout(category = WorkoutCategory.GYM, startedAt = started.minusSeconds(3600), durationMinutes = 45))
            manager.transaction.commit()
            manager.clear()
            val page = PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "startedAt", "id"))
            val first = repo.findAllBy(page)
            assertEquals(rope.id, first.content.single().id)
            assertTrue(first.hasNext())
            assertEquals(
                run.id,
                repo
                    .findAllBy(page.next())
                    .content
                    .single()
                    .id,
            )
            val filtered = repo.findAllByCategory(WorkoutCategory.RUNNING, page)
            assertFalse(filtered.hasNext())
            assertEquals(BigDecimal("5.125"), filtered.content.single().distanceKm)
            assertEquals(started, filtered.content.single().startedAt)
            assertEquals("/covers/workouts/remember.jpg", filtered.content.single().photoUrl)
            val jdbc = JdbcTemplate(source)
            assertFailsWith<org.springframework.dao.DataIntegrityViolationException> {
                jdbc.update("INSERT INTO workouts(category, started_at, duration_minutes) VALUES ('RUNNING', now(), 30)")
            }
            assertFailsWith<org.springframework.dao.DataIntegrityViolationException> {
                jdbc.update("INSERT INTO workouts(category, started_at, duration_minutes, jumps) VALUES ('GYM', now(), 30, 10)")
            }
        } finally {
            manager.close()
            factory.destroy()
        }
    }

    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }
}
