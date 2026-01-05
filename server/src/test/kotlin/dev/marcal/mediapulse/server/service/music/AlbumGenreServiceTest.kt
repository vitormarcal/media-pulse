package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Genre
import dev.marcal.mediapulse.server.model.music.GenreSource
import dev.marcal.mediapulse.server.repository.crud.AlbumGenreRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumGenreSourceRepository
import dev.marcal.mediapulse.server.repository.crud.GenreRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AlbumGenreServiceTest {
    @MockK lateinit var genreRepository: GenreRepository

    @MockK lateinit var albumGenreRepository: AlbumGenreRepository

    @MockK lateinit var albumGenreSourceRepository: AlbumGenreSourceRepository

    private lateinit var service: AlbumGenreService

    private val album =
        Album(
            id = 10L,
            artistId = 1L,
            title = "Any",
            titleKey = "any",
            fingerprint = "fp",
        )

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        service =
            AlbumGenreService(
                genreRepository = genreRepository,
                albumGenreRepository = albumGenreRepository,
                albumGenreSourceRepository = albumGenreSourceRepository,
            )
    }

    @Test
    fun `should return early when genres is null`() =
        runBlocking {
            service.addGenres(
                album = album,
                genres = null,
                source = GenreSource.PLEX,
            )

            verify(exactly = 0) { genreRepository.findByName(any()) }
            verify(exactly = 0) { genreRepository.save(any()) }
            verify(exactly = 0) { albumGenreRepository.findGenreIdsByAlbum(any()) }
            verify(exactly = 0) { albumGenreRepository.insert(any(), any()) }
            verify(exactly = 0) { albumGenreSourceRepository.insert(any(), any(), any()) }
        }

    @Test
    fun `should return early when genres is empty`() =
        runBlocking {
            service.addGenres(
                album = album,
                genres = emptyList(),
                source = GenreSource.PLEX,
            )

            verify(exactly = 0) { genreRepository.findByName(any()) }
            verify(exactly = 0) { genreRepository.save(any()) }
            verify(exactly = 0) { albumGenreRepository.findGenreIdsByAlbum(any()) }
            verify(exactly = 0) { albumGenreRepository.insert(any(), any()) }
            verify(exactly = 0) { albumGenreSourceRepository.insert(any(), any(), any()) }
        }

    @Test
    fun `should return early when normalized list becomes empty`() =
        runBlocking {
            service.addGenres(
                album = album,
                genres = listOf("   ", "\n", "\t"),
                source = GenreSource.PLEX,
            )

            verify(exactly = 0) { genreRepository.findByName(any()) }
            verify(exactly = 0) { genreRepository.save(any()) }
            verify(exactly = 0) { albumGenreRepository.findGenreIdsByAlbum(any()) }
            verify(exactly = 0) { albumGenreRepository.insert(any(), any()) }
            verify(exactly = 0) { albumGenreSourceRepository.insert(any(), any(), any()) }
        }

    @Test
    fun `should normalize, create missing genres, insert only missing album genres, and always insert sources`() =
        runBlocking {
            // input: duplicates, different casing, spaces, blanks
            val input = listOf("  Rock  ", "ROCK", " punk", "PUNK ", "", "   ")

            // normalized should become: ["rock", "punk"] in this order
            val existingRock = Genre(id = 100L, name = "rock")

            every { genreRepository.findByName("rock") } returns existingRock
            every { genreRepository.findByName("punk") } returns null

            val savedPunkSlot = slot<Genre>()
            every { genreRepository.save(capture(savedPunkSlot)) } answers {
                // emulate DB assigning an id
                val g = savedPunkSlot.captured
                Genre(id = 200L, name = g.name)
            }

            every { albumGenreRepository.findGenreIdsByAlbum(album.id) } returns setOf(100L) // rock already present
            every { albumGenreRepository.insert(any(), any()) } just runs
            every { albumGenreSourceRepository.insert(any(), any(), any()) } just runs

            service.addGenres(
                album = album,
                genres = input,
                source = GenreSource.MUSICBRAINZ,
            )

            // findByName called only for normalized distinct values
            verify(exactly = 1) { genreRepository.findByName("rock") }
            verify(exactly = 1) { genreRepository.findByName("punk") }

            // save only for missing (punk)
            verify(exactly = 1) { genreRepository.save(any()) }
            assertEquals("punk", savedPunkSlot.captured.name)

            // album_genres: insert only missing ids (punk=200)
            verify(exactly = 1) {
                albumGenreRepository.insert(
                    album.id,
                    match { it == listOf(200L) },
                )
            }

            // album_genre_sources: insert ALL processed ids (rock + punk), order matters by our flow
            verify(exactly = 1) {
                albumGenreSourceRepository.insert(
                    album.id,
                    match { it == listOf(100L, 200L) },
                    GenreSource.MUSICBRAINZ,
                )
            }
        }

    @Test
    fun `should not call insert on album_genres when all genres already exist in album`() =
        runBlocking {
            val input = listOf("Rock", "rock", "  rock  ")

            val rock = Genre(id = 100L, name = "rock")
            every { genreRepository.findByName("rock") } returns rock

            every { albumGenreRepository.findGenreIdsByAlbum(album.id) } returns setOf(100L)
            every { albumGenreRepository.insert(any(), any()) } just runs
            every { albumGenreSourceRepository.insert(any(), any(), any()) } just runs

            service.addGenres(
                album = album,
                genres = input,
                source = GenreSource.PLEX,
            )

            // only one normalized value -> one lookup
            verify(exactly = 1) { genreRepository.findByName("rock") }
            verify(exactly = 0) { genreRepository.save(any()) }

            verify(exactly = 0) {
                albumGenreRepository.insert(any(), any())
            }

            // provenance always gets the processed ids
            verify(exactly = 1) {
                albumGenreSourceRepository.insert(
                    album.id,
                    match { it == listOf(100L) },
                    GenreSource.PLEX,
                )
            }
        }
}
