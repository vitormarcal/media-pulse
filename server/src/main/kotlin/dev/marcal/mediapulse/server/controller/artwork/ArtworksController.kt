package dev.marcal.mediapulse.server.controller.artwork

import dev.marcal.mediapulse.server.api.artwork.ArtworkArtistRequest
import dev.marcal.mediapulse.server.api.artwork.ArtworkExperienceRequest
import dev.marcal.mediapulse.server.api.artwork.ArtworkFavoriteRequest
import dev.marcal.mediapulse.server.api.artwork.ArtworkImageUrlRequest
import dev.marcal.mediapulse.server.api.artwork.ArtworkListRequest
import dev.marcal.mediapulse.server.api.artwork.ArtworkRequest
import dev.marcal.mediapulse.server.model.artwork.ArtworkArtist
import dev.marcal.mediapulse.server.model.artwork.ArtworkImage
import dev.marcal.mediapulse.server.service.artwork.ArtworkImageService
import dev.marcal.mediapulse.server.service.artwork.ArtworkService
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
@RequestMapping("/api/artworks")
class ArtworksController(
    private val service: ArtworkService,
    private val images: ArtworkImageService,
) {
    @ExceptionHandler(ResponseStatusException::class)
    fun invalid(error: ResponseStatusException): ResponseEntity<ProblemDetail> =
        ResponseEntity
            .status(error.statusCode)
            .body(ProblemDetail.forStatusAndDetail(error.statusCode, error.reason ?: "Confira os dados da obra."))

    @GetMapping
    fun library(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) artistId: Long?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) movement: String?,
        @RequestParam(required = false) favorite: Boolean?,
        @RequestParam(required = false) listId: Long?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "24") limit: Int,
        @RequestParam(defaultValue = "recent") sort: String,
    ) = service.library(q, artistId, type, movement, favorite, listId, page, limit, sort)

    @GetMapping("/filters")
    fun filters() = service.filters()

    @GetMapping("/{id}")
    fun details(
        @PathVariable id: Long,
    ) = service.details(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestBody request: ArtworkRequest,
    ) = service.create(request)

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody request: ArtworkRequest,
    ) = service.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: Long,
    ) = service.delete(id)

    @PutMapping("/{id}/favorite")
    fun favorite(
        @PathVariable id: Long,
        @RequestBody request: ArtworkFavoriteRequest,
    ) = service.favorite(id, request.favorite)

    @GetMapping("/artists")
    fun artists() = service.artists()

    @GetMapping("/artists/{id}")
    fun artist(
        @PathVariable id: Long,
    ) = service.artist(id)

    @PostMapping("/artists")
    @ResponseStatus(HttpStatus.CREATED)
    fun createArtist(
        @RequestBody request: ArtworkArtistRequest,
    ) = service.saveArtist(null, request)

    @PutMapping("/artists/{id}")
    fun updateArtist(
        @PathVariable id: Long,
        @RequestBody request: ArtworkArtistRequest,
    ) = service.saveArtist(id, request)

    @DeleteMapping("/artists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteArtist(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "false") detach: Boolean,
    ) = service.deleteArtist(id, detach)

    @PostMapping("/artists/{id}/portrait", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun portrait(
        @PathVariable id: Long,
        @RequestPart("image") image: MultipartFile,
    ): ArtworkArtist {
        service.artist(id)
        return service.portrait(id, images.upload(image))
    }

    @PostMapping("/artists/{id}/portrait/url")
    fun portraitUrl(
        @PathVariable id: Long,
        @RequestBody request: ArtworkImageUrlRequest,
    ): ArtworkArtist {
        service.artist(id)
        return service.portrait(id, images.download(request.url))
    }

    @DeleteMapping("/artists/{id}/portrait")
    fun removePortrait(
        @PathVariable id: Long,
    ) = service.portrait(id, null)

    @PostMapping("/{id}/experiences")
    @ResponseStatus(HttpStatus.CREATED)
    fun createExperience(
        @PathVariable id: Long,
        @RequestBody request: ArtworkExperienceRequest,
    ) = service.saveExperience(id, null, request)

    @PutMapping("/{id}/experiences/{experienceId}")
    fun updateExperience(
        @PathVariable id: Long,
        @PathVariable experienceId: Long,
        @RequestBody request: ArtworkExperienceRequest,
    ) = service.saveExperience(id, experienceId, request)

    @DeleteMapping("/{id}/experiences/{experienceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteExperience(
        @PathVariable id: Long,
        @PathVariable experienceId: Long,
    ) = service.deleteExperience(id, experienceId)

    @PostMapping("/{id}/images", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(
        @PathVariable id: Long,
        @RequestPart("image") image: MultipartFile,
        @RequestParam(required = false) caption: String?,
    ): ArtworkImage {
        service.work(id)
        return images.attach(id, images.upload(image), null, caption)
    }

    @PostMapping("/{id}/images/url")
    @ResponseStatus(HttpStatus.CREATED)
    fun imageUrl(
        @PathVariable id: Long,
        @RequestBody request: ArtworkImageUrlRequest,
    ): ArtworkImage {
        service.work(id)
        return images.attach(id, images.download(request.url), request.url.trim(), request.caption)
    }

    @PutMapping("/{id}/images/{imageId}/primary")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun primary(
        @PathVariable id: Long,
        @PathVariable imageId: Long,
    ) = images.primary(id, imageId)

    @DeleteMapping("/{id}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeImage(
        @PathVariable id: Long,
        @PathVariable imageId: Long,
    ) = images.remove(id, imageId)

    @GetMapping("/lists")
    fun lists() = service.lists()

    @GetMapping("/lists/{id}")
    fun list(
        @PathVariable id: Long,
    ) = service.list(id)

    @PostMapping("/lists")
    @ResponseStatus(HttpStatus.CREATED)
    fun createList(
        @RequestBody request: ArtworkListRequest,
    ) = service.saveList(null, request)

    @PutMapping("/lists/{id}")
    fun updateList(
        @PathVariable id: Long,
        @RequestBody request: ArtworkListRequest,
    ) = service.saveList(id, request)

    @DeleteMapping("/lists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteList(
        @PathVariable id: Long,
    ) = service.deleteList(id)

    @PutMapping("/{id}/lists/{listId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun addToList(
        @PathVariable id: Long,
        @PathVariable listId: Long,
    ) = service.addToList(id, listId)

    @DeleteMapping("/{id}/lists/{listId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeFromList(
        @PathVariable id: Long,
        @PathVariable listId: Long,
    ) = service.removeFromList(id, listId)
}
