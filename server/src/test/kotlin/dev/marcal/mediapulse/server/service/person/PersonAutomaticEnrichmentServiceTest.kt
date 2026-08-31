package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.person.Person
import dev.marcal.mediapulse.server.repository.PersonProfileRepository
import dev.marcal.mediapulse.server.repository.crud.PersonRepository
import dev.marcal.mediapulse.server.service.movie.ManualMovieCatalogService
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonAutomaticEnrichmentServiceTest {
    private val people = mockk<PersonRepository>()
    private val profiles = mockk<PersonProfileRepository>(relaxed = true)
    private val tmdb = mockk<TmdbApiClient>()
    private val catalog = mockk<ManualMovieCatalogService>()
    private val tx = mockk<TxUtil>()
    private val service = PersonAutomaticEnrichmentService(people, profiles, tmdb, catalog, tx)

    init {
        every { tx.inTx<Any>(any()) } answers { firstArg<() -> Any>().invoke() }
    }

    @Test
    fun `should persist tmdb profile and mark enrichment complete`() {
        val person = Person(id = 44, tmdbId = "138", name = "Quentin Tarantino", normalizedName = "quentin tarantino", slug = "quentin")
        val saved = slot<Person>()
        every { people.findById(44) } returns Optional.of(person)
        every { tmdb.fetchPersonDetails("138") } returns tmdbProfile()
        every { catalog.buildTmdbImageUrl("/qt.jpg") } returns "https://image.tmdb.org/qt.jpg"
        every { people.save(capture(saved)) } answers { saved.captured }

        assertTrue(service.enrichPerson(44))

        assertEquals("Biografia curta.", saved.captured.biography)
        assertEquals("nm0000233", saved.captured.imdbId)
        assertEquals("https://image.tmdb.org/qt.jpg", saved.captured.profileUrl)
        assertNotNull(saved.captured.tmdbSyncedAt)
        assertNull(saved.captured.tmdbSyncError)
        verify { profiles.replaceAliases(44, listOf("QT")) }
    }

    @Test
    fun `should record failed attempt without completing enrichment`() {
        val person = Person(id = 44, tmdbId = "138", name = "Quentin Tarantino", normalizedName = "quentin tarantino", slug = "quentin")
        val saved = slot<Person>()
        every { people.findById(44) } returns Optional.of(person)
        every { tmdb.fetchPersonDetails("138") } returns null
        every { people.save(capture(saved)) } answers { saved.captured }

        assertFalse(service.enrichPerson(44))

        assertNull(saved.captured.tmdbSyncedAt)
        assertNotNull(saved.captured.tmdbSyncAttemptedAt)
        assertEquals("TMDb profile unavailable", saved.captured.tmdbSyncError)
        verify(exactly = 0) { profiles.replaceAliases(any(), any()) }
    }

    private fun tmdbProfile() =
        TmdbApiClient.TmdbPersonDetails(
            tmdbId = "138",
            name = "Quentin Tarantino",
            biography = "Biografia curta.",
            birthday = "1963-03-27",
            deathday = null,
            placeOfBirth = "Knoxville, Tennessee, USA",
            knownForDepartment = "Directing",
            alsoKnownAs = listOf("QT"),
            homepage = "https://example.com",
            imdbId = "nm0000233",
            popularity = 18.4,
            profilePath = "/qt.jpg",
        )
}
