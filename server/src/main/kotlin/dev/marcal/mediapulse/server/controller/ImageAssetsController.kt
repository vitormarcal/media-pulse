package dev.marcal.mediapulse.server.controller

import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class ImageAssetsController(
    private val client: VisualLibraryClient,
) {
    @GetMapping("/api/image-assets/{id}/content")
    fun content(
        @PathVariable id: UUID,
        @RequestHeader(HttpHeaders.IF_NONE_MATCH, required = false) etag: String?,
        @RequestHeader(HttpHeaders.IF_MODIFIED_SINCE, required = false) modifiedSince: String?,
    ) = client.content(id, etag, modifiedSince)
}
