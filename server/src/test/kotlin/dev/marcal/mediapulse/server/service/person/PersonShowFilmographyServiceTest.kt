package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.person.Person
import dev.marcal.mediapulse.server.repository.crud.PersonRepository
import dev.marcal.mediapulse.server.repository.crud.ShowCreditAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.ShowCreditsCrudRepository
import dev.marcal.mediapulse.server.service.tv.ManualShowCatalogService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals

class PersonShowFilmographyServiceTest {
    private val personRepository = mockk<PersonRepository>()
    private val showCreditAssignmentRepository = mockk<ShowCreditAssignmentRepository>(relaxed = true)
    private val showCreditsCrudRepository = mockk<ShowCreditsCrudRepository>()
    private val tmdbApiClient = mockk<TmdbApiClient>()
    private val manualShowCatalogService = mockk<ManualShowCatalogService>()

    private val service =
        PersonShowFilmographyService(
            personRepository = personRepository,
            showCreditAssignmentRepository = showCreditAssignmentRepository,
            showCreditsCrudRepository = showCreditsCrudRepository,
            tmdbApiClient = tmdbApiClient,
            manualShowCatalogService = manualShowCatalogService,
        )

    @Test
    fun `fetch filmography should reconcile local show credits before returning`() {
        val person =
            Person(
                id = 44,
                tmdbId = "138",
                name = "Quentin Tarantino",
                normalizedName = "quentin tarantino",
                slug = "quentin-tarantino-138",
                profileUrl = null,
            )

        every { personRepository.findById(44) } returns Optional.of(person)
        every { tmdbApiClient.fetchPersonTvCredits("138") } returns
            TmdbApiClient.TmdbPersonTvCredits(
                cast =
                    listOf(
                        TmdbApiClient.TmdbPersonTvCastCredit(
                            tmdbId = "901",
                            title = "Show A",
                            originalTitle = "Show A",
                            overview = null,
                            releaseYear = 2019,
                            posterPath = null,
                            backdropPath = null,
                            character = "Narrador",
                            order = 6,
                        ),
                    ),
                crew =
                    listOf(
                        TmdbApiClient.TmdbPersonTvCrewCredit(
                            tmdbId = "902",
                            title = "Show B",
                            originalTitle = "Show B",
                            overview = null,
                            releaseYear = 2021,
                            posterPath = null,
                            backdropPath = null,
                            department = "Writing",
                            job = "Writer",
                        ),
                    ),
            )
        every { showCreditsCrudRepository.findLocalShowsByTmdbIds(listOf("902", "901")) } returns
            mapOf(
                "901" to ShowCreditsCrudRepository.LocalShowByTmdbId(tmdbId = "901", showId = 29, slug = "show-a"),
                "902" to ShowCreditsCrudRepository.LocalShowByTmdbId(tmdbId = "902", showId = 31, slug = "show-b"),
            )

        val response = service.fetchFilmography(44)

        assertEquals(2, response.members.size)
        verify(exactly = 2) { showCreditAssignmentRepository.upsert(any()) }
        verify {
            showCreditAssignmentRepository.upsert(
                match {
                    it.showId == 29L &&
                        it.personId == 44L &&
                        it.characterName == "Narrador"
                },
            )
        }
        verify {
            showCreditAssignmentRepository.upsert(
                match {
                    it.showId == 31L &&
                        it.personId == 44L &&
                        it.job == "Writer"
                },
            )
        }
    }
}
