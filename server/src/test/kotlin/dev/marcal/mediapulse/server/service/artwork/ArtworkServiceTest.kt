package dev.marcal.mediapulse.server.service.artwork

import dev.marcal.mediapulse.server.api.artwork.ArtworkExperienceRequest
import dev.marcal.mediapulse.server.api.artwork.ArtworkRequest
import dev.marcal.mediapulse.server.model.artwork.Artwork
import dev.marcal.mediapulse.server.model.artwork.ArtworkArtist
import dev.marcal.mediapulse.server.model.artwork.ArtworkExperience
import dev.marcal.mediapulse.server.repository.MediaRatingQueryRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkArtistRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkExperienceRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkImageRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkListItemRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkListRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkRepository
import dev.marcal.mediapulse.server.repository.crud.MediaRatingRepository
import dev.marcal.mediapulse.server.repository.query.ArtworkQueryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArtworkServiceTest {
    private val works = mockk<ArtworkRepository>()
    private val artists = mockk<ArtworkArtistRepository>()
    private val experiences = mockk<ArtworkExperienceRepository>()
    private val images = mockk<ArtworkImageRepository>()
    private val lists = mockk<ArtworkListRepository>()
    private val items = mockk<ArtworkListItemRepository>()
    private val ratings = mockk<MediaRatingQueryRepository>()
    private val query = mockk<ArtworkQueryRepository>()
    private val ratingRepository = mockk<MediaRatingRepository>()
    private val service = ArtworkService(works, artists, experiences, images, lists, items, query, ratings, ratingRepository)

    @Test
    fun `minimal capture creates no invented experience and notes alone preserve unknown date and contact`() {
        every { works.save(any()) } answers { firstArg<Artwork>().copy(id = 1) }
        every { works.findById(1) } returns Optional.of(Artwork(id = 1, title = "Obra"))
        every { experiences.findByArtworkIdOrderByExperiencedAtDescIdDesc(1) } returns emptyList()
        every { images.findByArtworkIdOrderByIdAsc(1) } returns emptyList()
        every { items.findByArtworkId(1) } returns emptyList()
        every { lists.findAllById(any<List<Long>>()) } returns emptyList()
        every { ratings.findByEntity(any(), 1) } returns null
        every { experiences.save(any()) } answers { firstArg() }
        service.create(ArtworkRequest(title = "Obra", experience = ArtworkExperienceRequest()))
        verify(exactly = 0) { experiences.save(any()) }
        service.create(ArtworkRequest(title = "Obra", experience = ArtworkExperienceRequest(notes = " Descobri em uma aula ")))
        verify { experiences.save(match { it.notes == "Descobri em uma aula" && it.experiencedAt == null && it.experienceType == null }) }
    }

    @Test
    fun `experience from another work cannot be edited or removed`() {
        every { works.findById(1) } returns Optional.of(Artwork(id = 1, title = "Obra"))
        every { experiences.findById(9) } returns Optional.of(ArtworkExperience(id = 9, artworkId = 2, notes = "Visita"))
        assertEquals(
            HttpStatus.NOT_FOUND,
            assertFailsWith<ResponseStatusException> {
                service.saveExperience(1, 9, ArtworkExperienceRequest(notes = "Alteração"))
            }.statusCode,
        )
        assertFailsWith<ResponseStatusException> { service.deleteExperience(1, 9) }
        verify(exactly = 0) { experiences.save(any()) }
        verify(exactly = 0) { experiences.delete(any()) }
    }

    @Test
    fun `artist deletion requires explicit detachment and preserves works`() {
        every { artists.findById(3) } returns Optional.of(ArtworkArtist(id = 3, name = "Artista"))
        every { works.findByArtistId(3) } returns listOf(Artwork(id = 1, title = "Obra", artistId = 3))
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> { service.deleteArtist(3, false) }.statusCode)
        every { works.save(any()) } answers { firstArg() }
        every { artists.deleteById(3) } returns Unit
        service.deleteArtist(3, true)
        verify { works.save(match { it.id == 1L && it.artistId == null }) }
        verify(exactly = 0) { works.deleteById(any()) }
    }

    @Test
    fun `invalid capture and unsafe source links are rejected`() {
        assertFailsWith<ResponseStatusException> { service.create(ArtworkRequest(title = "  ")) }
        every { works.findById(1) } returns Optional.of(Artwork(id = 1, title = "Obra"))
        assertFailsWith<ResponseStatusException> {
            service.saveExperience(
                1,
                null,
                ArtworkExperienceRequest(sourceUrl = "javascript:alert(1)"),
            )
        }
        assertFailsWith<ResponseStatusException> { service.saveExperience(1, null, ArtworkExperienceRequest()) }
        verify(exactly = 0) { experiences.save(any()) }
    }
}
