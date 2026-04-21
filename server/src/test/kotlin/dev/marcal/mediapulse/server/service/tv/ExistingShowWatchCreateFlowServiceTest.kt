package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ExistingShowWatchCreateRequest
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.tv.TvEpisode
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExistingShowWatchCreateFlowServiceTest {
    private val tvShowRepository = mockk<TvShowRepository>()
    private val tvEpisodeRepository = mockk<TvEpisodeRepository>()
    private val manualShowWatchRegistrationService = mockk<ManualShowWatchRegistrationService>()
    private val externalIdentifierRepository = mockk<ExternalIdentifierRepository>()

    private val service =
        ExistingShowWatchCreateFlowService(
            tvShowRepository = tvShowRepository,
            tvEpisodeRepository = tvEpisodeRepository,
            manualShowWatchRegistrationService = manualShowWatchRegistrationService,
            externalIdentifierRepository = externalIdentifierRepository,
        )

    @Test
    fun `registra episodio manual para serie existente sem criar serie`() {
        val watchedAt = Instant.parse("2024-01-03T14:35:00Z")
        val show =
            TvShow(
                id = 17,
                originalTitle = "葬送のフリーレン",
                year = 2023,
                coverUrl = "/covers/plex/tv-shows/17/poster.jpg",
                fingerprint = "show-fp",
            )
        val episode =
            TvEpisode(
                id = 1359,
                showId = 17,
                title = "Episódio 1",
                seasonNumber = 1,
                episodeNumber = 1,
                fingerprint = "episode-fp",
            )
        val request =
            ExistingShowWatchCreateRequest(
                watchedAt = watchedAt,
                episodeTitle = "Episódio 1",
                seasonNumber = 1,
                episodeNumber = 1,
            )

        every { tvShowRepository.findById(17) } returns Optional.of(show)
        every { tvEpisodeRepository.findByFingerprint(any()) } returns null
        every { tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(17, 1, 1) } returns null
        every {
            tvEpisodeRepository.save(
                match {
                    it.showId == 17L &&
                        it.title == "Episódio 1" &&
                        it.seasonNumber == 1 &&
                        it.episodeNumber == 1
                },
            )
        } returns episode
        every { manualShowWatchRegistrationService.register(1359, watchedAt) } returns true
        every { externalIdentifierRepository.findByEntityTypeAndEntityId(EntityType.SHOW, 17) } returns
            listOf(ExternalIdentifier(entityType = EntityType.SHOW, entityId = 17, provider = Provider.TMDB, externalId = "209867"))

        val response = service.execute(17, request)

        assertEquals(17, response.showId)
        assertEquals(1359, response.episodeId)
        assertEquals("MANUAL", response.source)
        assertFalse(response.createdShow)
        assertTrue(response.createdEpisode)
        assertTrue(response.watchInserted)
        assertEquals("TMDB", response.externalIds.first().provider)
        verify(exactly = 0) { tvShowRepository.save(any()) }
    }

    @Test
    fun `reusa episodio existente da serie por temporada e numero`() {
        val watchedAt = Instant.parse("2024-01-03T14:35:00Z")
        val show = TvShow(id = 17, originalTitle = "葬送のフリーレン", year = 2023, fingerprint = "show-fp")
        val episode =
            TvEpisode(
                id = 1359,
                showId = 17,
                title = "A Jornada Começa",
                seasonNumber = 1,
                episodeNumber = 1,
                fingerprint = "episode-fp",
            )
        val request =
            ExistingShowWatchCreateRequest(
                watchedAt = watchedAt,
                episodeTitle = "Episódio 1",
                seasonNumber = 1,
                episodeNumber = 1,
            )

        every { tvShowRepository.findById(17) } returns Optional.of(show)
        every { tvEpisodeRepository.findByFingerprint(any()) } returns null
        every { tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(17, 1, 1) } returns episode
        every { manualShowWatchRegistrationService.register(1359, watchedAt) } returns false
        every { externalIdentifierRepository.findByEntityTypeAndEntityId(EntityType.SHOW, 17) } returns emptyList()

        val response = service.execute(17, request)

        assertEquals("A Jornada Começa", response.episodeTitle)
        assertFalse(response.createdShow)
        assertFalse(response.createdEpisode)
        assertFalse(response.watchInserted)
        verify(exactly = 0) { tvEpisodeRepository.save(any()) }
        verify(exactly = 0) { tvShowRepository.save(any()) }
    }
}
