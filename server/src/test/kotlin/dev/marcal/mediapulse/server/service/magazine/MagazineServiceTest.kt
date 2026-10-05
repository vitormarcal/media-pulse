package dev.marcal.mediapulse.server.service.magazine

import dev.marcal.mediapulse.server.api.magazine.MagazineDetailsDto
import dev.marcal.mediapulse.server.api.magazine.MagazineIssueRequest
import dev.marcal.mediapulse.server.api.magazine.MagazineReadRequest
import dev.marcal.mediapulse.server.model.magazine.MagazineIssue
import dev.marcal.mediapulse.server.model.magazine.MagazineRead
import dev.marcal.mediapulse.server.model.magazine.MagazineReadStatus
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MagazineServiceTest {
    private val tx = mockk<TxUtil>()
    private val covers = mockk<MagazineCoverService>(relaxed = true)
    private val service = MagazineService(mockk(), mockk(), mockk(), mockk(), mockk(), mockk(), covers, tx)
    private val issue = MagazineIssue(id = 7, publicationId = 1, number = "100", identityKey = "n:100", totalPages = 80)
    private val start = LocalDate.of(2026, 9, 1)
    private val end = LocalDate.of(2026, 9, 12)

    @Test
    fun `direct completion gives identical dates while subsequent completion preserves start`() {
        val direct = service.normalizeRead(issue, null, MagazineReadRequest(MagazineReadStatus.READ, finishedAt = end))
        assertEquals(end, direct.startedAt)
        assertEquals(end, direct.finishedAt)
        assertEquals(100.0, direct.progressPct)
        val current =
            MagazineRead(
                id = 3,
                issueId = 7,
                status = MagazineReadStatus.CURRENTLY_READING,
                startedAt = start,
                currentPage = 20,
                progressPct = 25.0,
            )
        val completed = service.normalizeRead(issue, current, MagazineReadRequest(MagazineReadStatus.READ, finishedAt = end))
        assertEquals(start, completed.startedAt)
        assertEquals(end, completed.finishedAt)
        assertEquals(current.id, completed.id)
    }

    @Test
    fun `pages calculate percentage and switching to percentage clears page without closing journey`() {
        val page =
            service.normalizeRead(
                issue,
                null,
                MagazineReadRequest(MagazineReadStatus.CURRENTLY_READING, startedAt = start, currentPage = 20),
            )
        assertEquals(25.0, page.progressPct)
        val percent = service.normalizeRead(issue, page, MagazineReadRequest(MagazineReadStatus.CURRENTLY_READING, progressPct = 100.0))
        assertEquals(null, percent.currentPage)
        assertEquals(MagazineReadStatus.CURRENTLY_READING, percent.status)
        assertEquals(null, percent.finishedAt)
        val noTotal =
            service.normalizeRead(
                issue.copy(totalPages = null),
                null,
                MagazineReadRequest(MagazineReadStatus.CURRENTLY_READING, startedAt = start, progressPct = 37.5),
            )
        assertEquals(37.5, noTotal.progressPct)
    }

    @Test
    fun `rejects invalid periods metrics and progress before a reading starts`() {
        val invalid =
            listOf(
                MagazineReadRequest(MagazineReadStatus.READ),
                MagazineReadRequest(MagazineReadStatus.READ, startedAt = end, finishedAt = start),
                MagazineReadRequest(MagazineReadStatus.CURRENTLY_READING),
                MagazineReadRequest(MagazineReadStatus.CURRENTLY_READING, startedAt = start, progressPct = -1.0),
                MagazineReadRequest(MagazineReadStatus.CURRENTLY_READING, startedAt = start, progressPct = Double.NaN),
                MagazineReadRequest(MagazineReadStatus.CURRENTLY_READING, startedAt = start, currentPage = 81),
                MagazineReadRequest(MagazineReadStatus.CURRENTLY_READING, startedAt = start, currentPage = 10, progressPct = 10.0),
                MagazineReadRequest(MagazineReadStatus.WANT_TO_READ, startedAt = start),
            )
        invalid.forEach { request ->
            assertEquals(400, assertFailsWith<ResponseStatusException> { service.normalizeRead(issue, null, request) }.statusCode.value())
        }
        assertFailsWith<ResponseStatusException> {
            service.normalizeRead(
                issue.copy(totalPages = null),
                null,
                MagazineReadRequest(MagazineReadStatus.CURRENTLY_READING, startedAt = start, currentPage = 10),
            )
        }
    }

    @Test
    fun `correcting started journey to want to read clears its dates and progress`() {
        val current =
            MagazineRead(
                id = 3,
                issueId = 7,
                status = MagazineReadStatus.CURRENTLY_READING,
                startedAt = start,
                currentPage = 20,
                progressPct = 25.0,
            )
        val corrected = service.normalizeRead(issue, current, MagazineReadRequest(MagazineReadStatus.WANT_TO_READ))
        assertEquals(3L, corrected.id)
        assertEquals(null, corrected.startedAt)
        assertEquals(null, corrected.finishedAt)
        assertEquals(null, corrected.currentPage)
        assertEquals(0.0, corrected.progressPct)
    }

    @Test
    fun `failed commit compensates uploaded cover`() {
        val cover = MockMultipartFile("cover", byteArrayOf(1))
        every { covers.save(cover) } returns "/covers/magazines/test.jpg"
        every { tx.inTx<MagazineDetailsDto>(any()) } throws IllegalStateException("commit failed")
        assertFailsWith<IllegalStateException> { service.create(MagazineIssueRequest(), cover) }
        verify { covers.delete("/covers/magazines/test.jpg") }
    }
}
