package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ShowSeasonEnrichmentApplyMode
import dev.marcal.mediapulse.server.api.shows.ShowSeasonEnrichmentApplyRequest
import dev.marcal.mediapulse.server.api.shows.ShowSeasonEnrichmentField
import dev.marcal.mediapulse.server.api.shows.ShowSeasonEnrichmentPreviewRequest
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
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
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShowSeasonMetadataEnrichmentServiceTest {
    private val tvShowRepository = mockk<TvShowRepository>()
    private val tvEpisodeRepository = mockk<TvEpisodeRepository>()
    private val externalIdentifierRepository = mockk<ExternalIdentifierRepository>()
    private val tmdbApiClient = mockk<TmdbApiClient>()

    private val service =
        ShowSeasonMetadataEnrichmentService(
            tvShowRepository = tvShowRepository,
            tvEpisodeRepository = tvEpisodeRepository,
            externalIdentifierRepository = externalIdentifierRepository,
            tmdbApiClient = tmdbApiClient,
        )

    @Test
    fun `preview marca titulo generico e descricao ausente como lacunas`() {
        every { tvShowRepository.findById(50) } returns Optional.of(show())
        every { tvEpisodeRepository.findByShowIdAndSeasonNumberOrderByEpisodeNumberAscIdAsc(50, 1) } returns
            listOf(
                episode(id = 100, title = "Episode 1", episodeNumber = 1),
                episode(id = 101, title = "Episode 2", episodeNumber = 2, summary = "Already described."),
            )
        every {
            externalIdentifierRepository.findFirstByEntityTypeAndProviderAndEntityId(EntityType.SHOW, Provider.TMDB, 50)
        } returns ExternalIdentifier(entityType = EntityType.SHOW, entityId = 50, provider = Provider.TMDB, externalId = "209867")
        every { tmdbApiClient.fetchShowSeasonDetails("209867", 1) } returns tmdbSeason()

        val response = service.preview(50, 1, ShowSeasonEnrichmentPreviewRequest())

        assertEquals("209867", response.resolvedTmdbId)
        assertEquals(2, response.changedEpisodesCount)
        assertTrue(response.selectedFieldsCount >= 3)
        val first = response.episodes.first()
        assertTrue(first.fields.first { it.field == ShowSeasonEnrichmentField.EPISODE_TITLE }.selectedByDefault)
        assertTrue(first.fields.first { it.field == ShowSeasonEnrichmentField.EPISODE_SUMMARY }.selectedByDefault)
    }

    @Test
    fun `apply missing atualiza somente lacunas dos episodios existentes`() {
        val genericEpisode = episode(id = 100, title = "Episódio 1", episodeNumber = 1)
        val describedEpisode = episode(id = 101, title = "The Existing Title", episodeNumber = 2, summary = "Already described.")

        every { tvShowRepository.existsById(50) } returns true
        every { tvEpisodeRepository.findByShowIdAndSeasonNumberOrderByEpisodeNumberAscIdAsc(50, 1) } returns
            listOf(genericEpisode, describedEpisode)
        every {
            externalIdentifierRepository.findFirstByEntityTypeAndProviderAndEntityId(EntityType.SHOW, Provider.TMDB, 50)
        } returns null
        every { externalIdentifierRepository.findByProviderAndExternalId(Provider.TMDB, "209867") } returns null
        every { externalIdentifierRepository.save(any()) } answers { firstArg() }
        every { tvEpisodeRepository.save(any()) } answers { firstArg() }
        every { tmdbApiClient.fetchShowSeasonDetails("209867", 1) } returns tmdbSeason()

        val response =
            service.apply(
                50,
                1,
                ShowSeasonEnrichmentApplyRequest(
                    tmdbId = "209867",
                    mode = ShowSeasonEnrichmentApplyMode.MISSING,
                ),
            )

        assertEquals(2, response.updatedEpisodesCount)
        assertTrue(response.appliedFieldsCount >= 4)
        verify {
            tvEpisodeRepository.save(
                match {
                    it.id == 100L &&
                        it.title == "The End's Beginning" &&
                        it.summary == "Frieren returns after the journey." &&
                        it.durationMs == 1_560_000 &&
                        it.originallyAvailableAt == LocalDate.parse("2023-09-29")
                },
            )
        }
        verify {
            tvEpisodeRepository.save(
                match {
                    it.id == 101L &&
                        it.title == "The Existing Title" &&
                        it.summary == "Already described." &&
                        it.durationMs == 1_500_000
                },
            )
        }
        verify {
            externalIdentifierRepository.save(
                match {
                    it.entityType == EntityType.SHOW &&
                        it.entityId == 50L &&
                        it.provider == Provider.TMDB &&
                        it.externalId == "209867"
                },
            )
        }
    }

    private fun show() =
        TvShow(
            id = 50,
            originalTitle = "Frieren e a Jornada para o Além",
            year = 2023,
            fingerprint = "show-fp",
        )

    private fun episode(
        id: Long,
        title: String,
        episodeNumber: Int,
        summary: String? = null,
    ) = TvEpisode(
        id = id,
        showId = 50,
        title = title,
        seasonNumber = 1,
        seasonTitle = "Temporada 1",
        episodeNumber = episodeNumber,
        summary = summary,
        fingerprint = "episode-$id",
    )

    private fun tmdbSeason() =
        TmdbApiClient.TmdbShowSeasonDetails(
            tmdbId = "355298",
            title = "Season 1",
            overview = null,
            seasonNumber = 1,
            airDate = "2023-09-29",
            posterPath = null,
            episodes =
                listOf(
                    TmdbApiClient.TmdbShowSeasonEpisode(
                        tmdbId = "4669872",
                        title = "The End's Beginning",
                        overview = "Frieren returns after the journey.",
                        episodeNumber = 1,
                        airDate = "2023-09-29",
                        runtimeMinutes = 26,
                    ),
                    TmdbApiClient.TmdbShowSeasonEpisode(
                        tmdbId = "4669873",
                        title = "It Didn't Have to Be Magic...",
                        overview = "Frieren looks back.",
                        episodeNumber = 2,
                        airDate = "2023-09-29",
                        runtimeMinutes = 25,
                    ),
                ),
        )
}
