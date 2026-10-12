package dev.marcal.mediapulse.server.controller.magazine

import dev.marcal.mediapulse.server.api.magazine.MagazineDetailsDto
import dev.marcal.mediapulse.server.api.magazine.MagazineIssueDto
import dev.marcal.mediapulse.server.api.magazine.MagazinePublicationDetailsDto
import dev.marcal.mediapulse.server.api.magazine.MagazinePublicationDto
import dev.marcal.mediapulse.server.service.magazine.MagazineService
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
import java.time.LocalDate

class MagazinesControllerTest {
    private val service = mockk<MagazineService>()
    private val mvc = MockMvcBuilders.standaloneSetup(MagazinesController(service)).build()
    private val result =
        MagazineDetailsDto(
            MagazineIssueDto(7, MagazinePublicationDto(1, "Revista", null), "100", null, null, null, LocalDate.of(2026, 10, 5), null),
            emptyList(),
            emptyList(),
        )

    @Test
    fun `cover retry accepts only the cover and exposes partial success`() {
        every { service.updateCover(7, any()) } returns result.copy(coverUploadError = "Tente novamente.")
        mvc
            .perform(multipart("/api/magazines/7/cover").file(MockMultipartFile("cover", byteArrayOf(1))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.issue.id").value(7))
            .andExpect(jsonPath("$.coverUploadError").value("Tente novamente."))
        mvc.perform(multipart("/api/magazines/7/cover")).andExpect(status().isBadRequest)
        verify(exactly = 0) { service.create(any(), any()) }
    }

    @Test
    fun `publication detail uses its dedicated route and unknown sort is rejected`() {
        every { service.publicationDetails(1) } returns
            MagazinePublicationDetailsDto(result.issue.publication.copy(numbersCount = 1), result.issue)
        mvc
            .perform(get("/api/magazines/publications/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.publication.numbersCount").value(1))
            .andExpect(jsonPath("$.latestIssue.id").value(7))
        mvc.perform(get("/api/magazines").param("order", "UNKNOWN")).andExpect(status().isBadRequest)
    }

    @Test
    fun `multipart issue creation and metadata update bind optional image`() {
        every { service.create(any(), any()) } returns result
        every { service.updateIssue(7, any(), any()) } returns result
        val payload =
            MockMultipartFile(
                "issue",
                "",
                "application/json",
                """{"publication":{"name":"Revista"},"number":"100","read":{"status":"READ","finishedAt":"2026-10-05"}}""".toByteArray(),
            )
        mvc.perform(multipart("/api/magazines").file(payload)).andExpect(status().isCreated).andExpect(jsonPath("$.issue.id").value(7))
        verify { service.create(match { it.read?.finishedAt == LocalDate.of(2026, 10, 5) }, null) }
        mvc
            .perform(
                multipart("/api/magazines/7").file(payload).with {
                    it.method = "PUT"
                    it
                },
            ).andExpect(status().isOk)
        verify { service.updateIssue(7, any(), null) }
    }

    @Test
    fun `fractional page counts are rejected instead of truncated`() {
        val payload =
            MockMultipartFile(
                "issue",
                "",
                "application/json",
                """{"publication":{"name":"Revista"},"number":"1","totalPages":80.5,"read":{"status":"WANT_TO_READ"}}""".toByteArray(),
            )
        mvc.perform(multipart("/api/magazines").file(payload)).andExpect(status().isBadRequest)
        mvc
            .perform(
                post(
                    "/api/magazines/7/reads",
                ).contentType("application/json").content("""{"status":"CURRENTLY_READING","startedAt":"2026-10-05","currentPage":2.5}"""),
            ).andExpect(status().isBadRequest)
        verify(exactly = 0) { service.create(any(), any()) }
        verify(exactly = 0) { service.createRead(any(), any()) }
    }

    @Test
    fun `invalid dates and states never enter service and domain errors expose detail`() {
        mvc
            .perform(
                post("/api/magazines/7/reads").contentType("application/json").content("""{"status":"OTHER"}"""),
            ).andExpect(status().isBadRequest)
        mvc
            .perform(
                post("/api/magazines/7/reads").contentType("application/json").content("""{"status":"READ","finishedAt":"not-a-date"}"""),
            ).andExpect(status().isBadRequest)
        verify(exactly = 0) { service.createRead(any(), any()) }
        every { service.createRead(any(), any()) } throws ResponseStatusException(HttpStatus.CONFLICT, "Jornada em aberto.")
        mvc
            .perform(
                post("/api/magazines/7/reads").contentType("application/json").content("""{"status":"WANT_TO_READ"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.detail").value("Jornada em aberto."))
    }
}
