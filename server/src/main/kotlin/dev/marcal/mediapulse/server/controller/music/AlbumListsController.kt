package dev.marcal.mediapulse.server.controller.music

import dev.marcal.mediapulse.server.api.music.AlbumListCreateRequest
import dev.marcal.mediapulse.server.api.music.AlbumListDetailsResponse
import dev.marcal.mediapulse.server.api.music.AlbumListListenedRequest
import dev.marcal.mediapulse.server.api.music.AlbumListOrderRequest
import dev.marcal.mediapulse.server.api.music.AlbumListSummaryDto
import dev.marcal.mediapulse.server.api.music.AlbumListUpdateRequest
import dev.marcal.mediapulse.server.service.music.AlbumListsService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/music/lists")
class AlbumListsController(
    private val service: AlbumListsService,
) {
    @GetMapping
    fun listAll(): List<AlbumListSummaryDto> = service.listAll()

    @GetMapping("/{slug}")
    fun details(
        @PathVariable slug: String,
    ): AlbumListDetailsResponse = service.details(slug)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: AlbumListCreateRequest,
    ): AlbumListDetailsResponse = service.create(request)

    @PutMapping("/{listId}")
    fun update(
        @PathVariable listId: Long,
        @RequestBody request: AlbumListUpdateRequest,
    ): AlbumListDetailsResponse = service.update(listId, request)

    @DeleteMapping("/{listId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable listId: Long,
    ) = service.delete(listId)

    @PostMapping("/{listId}/albums/{albumId}")
    fun addAlbum(
        @PathVariable listId: Long,
        @PathVariable albumId: Long,
    ): AlbumListDetailsResponse = service.addAlbum(listId, albumId)

    @DeleteMapping("/{listId}/albums/{albumId}")
    fun removeAlbum(
        @PathVariable listId: Long,
        @PathVariable albumId: Long,
    ): AlbumListDetailsResponse = service.removeAlbum(listId, albumId)

    @PutMapping("/{listId}/order")
    fun updateOrder(
        @PathVariable listId: Long,
        @RequestBody request: AlbumListOrderRequest,
    ): AlbumListDetailsResponse = service.updateOrder(listId, request)

    @PatchMapping("/{listId}/albums/{albumId}/listened")
    fun updateListened(
        @PathVariable listId: Long,
        @PathVariable albumId: Long,
        @RequestBody request: AlbumListListenedRequest,
    ): AlbumListDetailsResponse = service.updateListened(listId, albumId, request)
}
