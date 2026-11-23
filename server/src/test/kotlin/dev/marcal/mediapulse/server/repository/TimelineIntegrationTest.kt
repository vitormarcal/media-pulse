package dev.marcal.mediapulse.server.repository

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.marcal.mediapulse.server.api.timeline.TimelineDomain
import dev.marcal.mediapulse.server.api.timeline.TimelineKind
import dev.marcal.mediapulse.server.api.timeline.TimelinePrecision
import dev.marcal.mediapulse.server.controller.TimelineController
import dev.marcal.mediapulse.server.repository.query.TimelineQueryRepository
import dev.marcal.mediapulse.server.service.timeline.TimelineService
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class TimelineIntegrationTest {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var service: TimelineService

    @BeforeEach
    fun setup() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        jdbc = JdbcTemplate(source)
        jdbc.execute("TRUNCATE artists, books, movies, tv_shows, games, magazine_publications, artworks, workouts RESTART IDENTITY CASCADE")
        service = TimelineService(TimelineQueryRepository(NamedParameterJdbcTemplate(source), jacksonObjectMapper()))
    }

    @Test
    fun `all domains preserve historical dates precision music grouping and separate local additions`() {
        jdbc.update("INSERT INTO artists(id,name,fingerprint) VALUES (1,'Artist','artist')")
        jdbc.update(
            "INSERT INTO albums(id,artist_id,title,title_key,fingerprint,cover_url,created_at) VALUES (1,1,'Album','album','album','/cover','2026-01-01Z')",
        )
        jdbc.update("INSERT INTO album_artists(album_id,position,artist_id,credited_name,source) VALUES (1,0,1,'Artist','LEGACY')")
        jdbc.update("INSERT INTO tracks(id,artist_id,title,fingerprint) VALUES (1,1,'Track','track')")
        jdbc.update(
            "INSERT INTO track_playbacks(track_id,album_id,source,played_at) VALUES (1,1,'PLEX','2020-03-01T02:30:00Z'),(1,1,'PLEX','2020-03-01T04:00:00Z'),(1,1,'PLEX','2020-03-01T05:00:00Z')",
        )
        jdbc.update("INSERT INTO books(id,title,slug,fingerprint,created_at) VALUES (1,'Book','book','book','2026-01-01Z')")
        jdbc.update(
            "INSERT INTO book_reads(book_id,source,source_session_id,status,started_at,finished_at) VALUES (1,'HARDCOVER',1,'READ','1995-01-01T12:00:00Z','1995-02-01T12:00:00Z'),(1,'HARDCOVER',2,'READ',NULL,'2000-01-01T12:00:00Z'),(1,'HARDCOVER',3,'WANT_TO_READ',NULL,NULL)",
        )
        jdbc.update("INSERT INTO movies(id,original_title,fingerprint,slug,created_at) VALUES (1,'Movie','movie','movie','2026-01-01Z')")
        jdbc.update("INSERT INTO movie_titles(movie_id,title,source,is_primary) VALUES (1,'Filme','PLEX',true)")
        jdbc.update("INSERT INTO movie_watches(movie_id,source,watched_at) VALUES (1,'PLEX','1970-01-01T12:00:00Z')")
        jdbc.update("INSERT INTO tv_shows(id,original_title,fingerprint,slug,created_at) VALUES (1,'Show','show','show','2026-01-01Z')")
        jdbc.update("INSERT INTO tv_episodes(id,show_id,title,season_number,episode_number,fingerprint) VALUES (1,1,'Pilot',1,1,'pilot')")
        jdbc.update(
            "INSERT INTO tv_episode_watches(episode_id,source,watched_at) VALUES (1,'PLEX','2010-01-01T12:00:00Z'),(1,'PLEX','2011-01-01T12:00:00Z')",
        )
        jdbc.update("INSERT INTO games(id,title,original_title,fingerprint,created_at) VALUES (1,'Game','Game','game','2026-01-01Z')")
        jdbc.update(
            "INSERT INTO game_sessions(game_id,status,started_at,ended_at) VALUES (1,'COMPLETED','2015-01-01T12:00:00Z','2015-01-02T12:00:00Z'),(1,'BACKLOG','2026-01-01T12:00:00Z',NULL)",
        )
        jdbc.update("INSERT INTO magazine_publications(id,name,normalized_name) VALUES (1,'Magazine','magazine')")
        jdbc.update("INSERT INTO magazine_issues(id,publication_id,number,identity_key) VALUES (1,1,'1','1')")
        jdbc.update(
            "INSERT INTO magazine_reads(issue_id,status,started_at,finished_at,progress_pct) VALUES (1,'READ','1997-01-01','1997-01-02',100)",
        )
        jdbc.update("INSERT INTO artworks(id,title,created_at) VALUES (1,'Artwork','2026-01-01Z')")
        jdbc.update(
            "INSERT INTO artwork_experiences(artwork_id,experienced_at,experience_type) VALUES (1,'2012-01-01','IN_PERSON'),(1,NULL,'DIGITAL_VIEWING')",
        )
        jdbc.update("INSERT INTO workouts(category,started_at,duration_minutes) VALUES ('GYM','2025-01-01T12:00:00Z',30)")

        val activities = service.page(null, TimelineKind.ACTIVITY, null, null, 100).items
        assertEquals(TimelineDomain.entries.toSet(), activities.map { it.domain }.toSet())
        assertEquals(14, activities.size)
        assertEquals(activities.map { it.date }.sortedDescending(), activities.map { it.date })
        assertEquals(LocalDate.of(1970, 1, 1), activities.last().date)
        assertEquals("Filme", activities.last().title)
        val music = activities.filter { it.domain == TimelineDomain.MUSIC }
        assertEquals(2, music.size)
        assertEquals("2 reproduções", music.first().subtitle)
        assertEquals(LocalDate.of(2020, 2, 29), music.last().date)
        assertEquals(
            setOf("/music/albums/1", "/music/artists/1"),
            music
                .first()
                .relatedItems
                .map { it.href }
                .toSet(),
        )
        assertNull(music.first().occurredAt)
        assertEquals(TimelinePrecision.DAY, music.first().precision)
        val magazine = activities.first { it.id.startsWith("magazine-start") }
        assertEquals(LocalDate.of(1997, 1, 1), magazine.date)
        assertNull(magazine.occurredAt)
        val additions = service.page(null, TimelineKind.ADDITION, null, null, 100).items
        assertEquals(6, additions.size)
        assertTrue(additions.all { it.action == "Incluído no arquivo local" })
        assertFalse(additions.any { it.domain == TimelineDomain.MAGAZINES || it.domain == TimelineDomain.WORKOUTS })
        val periods = service.periods(null, TimelineKind.ACTIVITY).months
        assertEquals(activities.size.toLong(), periods.sumOf { it.count })
        assertEquals("1970-01", periods.last().month)
        assertEquals(listOf("1997-01"), service.periods(TimelineDomain.MAGAZINES, TimelineKind.ACTIVITY).months.map { it.month })
        assertTrue(service.page(null, TimelineKind.ACTIVITY, "1995-01", null, 100).items.all { it.date <= LocalDate.of(1995, 1, 31) })
    }

    @Test
    fun `cursor handles equal timestamps older pages and concurrent newer insertion`() {
        jdbc.update("INSERT INTO movies(id,original_title,fingerprint) VALUES (1,'Movie','movie')")
        for (index in 1..5) {
            jdbc.update(
                "INSERT INTO movie_watches(movie_id,source,watched_at) VALUES (1,'PLEX',?::timestamptz)",
                "2000-01-01T12:00:0${index}Z",
            )
        }
        jdbc.update("INSERT INTO workouts(category,started_at,duration_minutes) VALUES ('GYM','2000-01-01T12:00:05Z',30)")
        val first = service.page(null, TimelineKind.ACTIVITY, null, null, 2)
        assertNotNull(first.nextCursor)
        jdbc.update("INSERT INTO movie_watches(movie_id,source,watched_at) VALUES (1,'PLEX','2026-01-01T12:00:00Z')")
        val second = service.page(null, TimelineKind.ACTIVITY, null, first.nextCursor, 2)
        val third = service.page(null, TimelineKind.ACTIVITY, null, second.nextCursor, 2)
        val ids = (first.items + second.items + third.items).map { it.id }
        assertEquals(6, ids.size)
        assertEquals(ids.size, ids.distinct().size)
        assertNull(third.nextCursor)
        assertFailsWith<ResponseStatusException> { service.page(TimelineDomain.MOVIES, TimelineKind.ACTIVITY, null, first.nextCursor, 2) }
    }

    @Test
    fun `HTTP contract rejects invalid filters dates cursors and limits`() {
        val mvc = MockMvcBuilders.standaloneSetup(TimelineController(service)).build()
        for (query in listOf("limit=0", "limit=101", "month=2026-13", "month=no", "cursor=bad", "domain=UNKNOWN", "kind=UNKNOWN")) {
            mvc.perform(get("/api/timeline?$query")).andExpect(status().isBadRequest)
        }
        mvc.perform(get("/api/timeline")).andExpect(status().isOk)
        mvc.perform(get("/api/timeline/periods")).andExpect(status().isOk)
    }

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
