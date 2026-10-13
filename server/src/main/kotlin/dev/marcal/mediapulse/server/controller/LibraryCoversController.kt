package dev.marcal.mediapulse.server.controller

import dev.marcal.mediapulse.server.api.LibraryCoverResponse
import dev.marcal.mediapulse.server.service.image.LibraryCoverService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
class LibraryCoversController(
    private val covers: LibraryCoverService,
) {
    @PostMapping("/api/books/{bookId}/cover", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun book(
        @PathVariable bookId: Long,
        @RequestPart cover: MultipartFile,
    ): LibraryCoverResponse = LibraryCoverResponse(covers.book(bookId, cover))

    @PostMapping("/api/games/{gameId}/cover", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun game(
        @PathVariable gameId: Long,
        @RequestPart cover: MultipartFile,
    ): LibraryCoverResponse = LibraryCoverResponse(covers.game(gameId, cover))
}
