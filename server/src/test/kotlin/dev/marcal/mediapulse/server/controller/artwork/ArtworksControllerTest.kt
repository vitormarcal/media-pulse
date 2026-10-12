package dev.marcal.mediapulse.server.controller.artwork

import dev.marcal.mediapulse.server.api.artwork.ArtworkDetailsDto
import dev.marcal.mediapulse.server.api.artwork.ArtworkLibraryDto
import dev.marcal.mediapulse.server.model.artwork.Artwork
import dev.marcal.mediapulse.server.model.artwork.ArtworkArtist
import dev.marcal.mediapulse.server.model.artwork.ArtworkImage
import dev.marcal.mediapulse.server.model.visuallibrary.VisualLibraryUploadMetadata
import dev.marcal.mediapulse.server.service.artwork.ArtworkImageService
import dev.marcal.mediapulse.server.service.artwork.ArtworkMaintenanceService
import dev.marcal.mediapulse.server.service.artwork.ArtworkService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException

class ArtworksControllerTest {
    private val service = mockk<ArtworkService>()
    private val images = mockk<ArtworkImageService>()
    private val maintenance = mockk<ArtworkMaintenanceService>()
    private val mvc = MockMvcBuilders.standaloneSetup(ArtworksController(service, images, maintenance)).build()

    @Test
    fun `movement payloads reject scalars null items and numeric items before saving`() {
        for (value in listOf("\"Simbolismo\"", "[null]", "[7]")) {
            mvc
                .perform(post("/api/artworks").contentType("application/json").content("""{"title":"X","movements":$value}"""))
                .andExpect(status().isBadRequest)
        }
        verify(exactly = 0) { service.create(any()) }
    }

    @Test
    fun `library forwards chronological sorting and preserves recent default`() {
        every { service.library(null, 7, null, null, null, null, 1, 2, "year") } returns ArtworkLibraryDto(emptyList(), null)
        every { service.library(null, null, null, null, null, null, 0, 24, "recent") } returns ArtworkLibraryDto(emptyList(), null)
        mvc
            .perform(
                get("/api/artworks")
                    .param("artistId", "7")
                    .param("sort", "year")
                    .param("page", "1")
                    .param("limit", "2"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isArray)
        mvc.perform(get("/api/artworks")).andExpect(status().isOk)
        verify { service.library(null, 7, null, null, null, null, 1, 2, "year") }
        verify { service.library(null, null, null, null, null, null, 0, 24, "recent") }
    }

    @Test
    fun `artist creation accepts country of origin in the public contract`() {
        every { service.saveArtist(null, any()) } returns ArtworkArtist(id = 1, name = "Artista", countryOfOrigin = "França")
        mvc
            .perform(
                post("/api/artworks/artists")
                    .contentType("application/json")
                    .content("""{"name":"Artista","countryOfOrigin":"França"}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.countryOfOrigin").value("França"))
        verify { service.saveArtist(null, match { it.countryOfOrigin == "França" }) }
    }

    @Test
    fun `JSON creation accepts partial experience and returns canonical detail`() {
        every { service.create(any()) } returns
            ArtworkDetailsDto(Artwork(id = 1, title = "Obra"), null, emptyList(), emptyList(), emptyList(), null)
        mvc
            .perform(
                post("/api/artworks").contentType("application/json").content("""{"title":"Obra","experience":{"notes":"Descoberta"}}"""),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.artwork.id").value(1))
        verify { service.create(match { it.experience?.notes == "Descoberta" && it.experience?.experiencedAt == null }) }
    }

    @Test
    fun `invalid dates and contact types do not enter service and missing detail is explicit`() {
        mvc
            .perform(post("/api/artworks/1/experiences").contentType("application/json").content("""{"experienceType":"WATCHING"}"""))
            .andExpect(status().isBadRequest)
        mvc
            .perform(post("/api/artworks/1/experiences").contentType("application/json").content("""{"experiencedAt":"not-a-date"}"""))
            .andExpect(status().isBadRequest)
        verify(exactly = 0) { service.saveExperience(any(), any(), any()) }
        every { service.details(404) } throws ResponseStatusException(HttpStatus.NOT_FOUND, "Obra não encontrada.")
        mvc.perform(get("/api/artworks/404")).andExpect(status().isNotFound).andExpect(jsonPath("$.detail").value("Obra não encontrada."))
    }

    @Test
    fun `file and URL image endpoints attach independently from work creation`() {
        every { service.work(1) } returns Artwork(id = 1, title = "Obra")
        val metadata = VisualLibraryUploadMetadata.forEntity("artwork", 1, "Obra")
        every { images.artworkMetadata(1) } returns metadata
        every { images.upload(any(), metadata) } returns "/covers/artworks/local.png"
        every { images.download("https://example.com/image.png", metadata) } returns "/covers/artworks/download.png"
        every { maintenance.attach(any(), any(), any(), any()) } answers { ArtworkImage(id = 1, artworkId = firstArg(), url = secondArg()) }
        mvc
            .perform(multipart("/api/artworks/1/images").file(MockMultipartFile("image", "image.png", "image/png", byteArrayOf(1))))
            .andExpect(status().isCreated)
        mvc
            .perform(
                post("/api/artworks/1/images/url").contentType("application/json").content("""{"url":"https://example.com/image.png"}"""),
            ).andExpect(status().isCreated)
        verify { maintenance.attach(1, "/covers/artworks/download.png", "https://example.com/image.png", null) }
    }
}
