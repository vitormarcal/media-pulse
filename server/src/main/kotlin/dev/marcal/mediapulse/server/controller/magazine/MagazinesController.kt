package dev.marcal.mediapulse.server.controller.magazine

import dev.marcal.mediapulse.server.api.magazine.MagazineIssueRequest
import dev.marcal.mediapulse.server.api.magazine.MagazinePublicationRequest
import dev.marcal.mediapulse.server.api.magazine.MagazineReadRequest
import dev.marcal.mediapulse.server.model.magazine.MagazineReadStatus
import dev.marcal.mediapulse.server.service.magazine.MagazineService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/magazines")
class MagazinesController(
    private val service: MagazineService,
) {
    @ExceptionHandler(ResponseStatusException::class)
    fun invalid(error: ResponseStatusException): ResponseEntity<ProblemDetail> =
        ResponseEntity.status(error.statusCode).body(
            ProblemDetail.forStatusAndDetail(
                error.statusCode,
                error.reason ?: "Confira os dados da revista.",
            ),
        )

    @GetMapping
    fun library(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) publicationId: Long?,
        @RequestParam(required = false) status: MagazineReadStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "24") limit: Int,
        @RequestParam(defaultValue = "false") unread: Boolean,
    ) = service.library(q, publicationId, status, page, limit, unread)

    @GetMapping("/overview")
    fun overview() = service.overview()

    @GetMapping("/publications")
    fun publications() = service.publications()

    @PutMapping("/publications/{id}")
    fun updatePublication(
        @PathVariable id: Long,
        @RequestBody request: MagazinePublicationRequest,
    ) = service.updatePublication(id, request)

    @GetMapping("/{id}")
    fun details(
        @PathVariable id: Long,
    ) = service.details(id)

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestPart("issue") request: MagazineIssueRequest,
        @RequestPart("cover", required = false) cover: MultipartFile?,
    ) = service.create(request, cover)

    @PutMapping("/{id}", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun update(
        @PathVariable id: Long,
        @RequestPart("issue") request: MagazineIssueRequest,
        @RequestPart("cover", required = false) cover: MultipartFile?,
    ) = service.updateIssue(id, request, cover)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
    ) {
        service.deleteIssue(id)
    }

    @PostMapping("/{id}/reads")
    @ResponseStatus(HttpStatus.CREATED)
    fun createRead(
        @PathVariable id: Long,
        @RequestBody request: MagazineReadRequest,
    ) = service.createRead(id, request)

    @PutMapping("/{id}/reads/{readId}")
    fun updateRead(
        @PathVariable id: Long,
        @PathVariable readId: Long,
        @RequestBody request: MagazineReadRequest,
    ) = service.updateRead(id, readId, request)

    @DeleteMapping("/{id}/reads/{readId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRead(
        @PathVariable id: Long,
        @PathVariable readId: Long,
    ) {
        service.deleteRead(id, readId)
    }
}
