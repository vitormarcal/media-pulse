package dev.marcal.mediapulse.server.controller.music

import dev.marcal.mediapulse.server.api.music.MusicBrainzAlbumApplyRequest
import dev.marcal.mediapulse.server.api.music.MusicBrainzArtistApplyRequest
import dev.marcal.mediapulse.server.api.music.MusicBrainzArtistCreateRequest
import dev.marcal.mediapulse.server.api.music.MusicBrainzDiscographyImportRequest
import dev.marcal.mediapulse.server.service.musicbrainz.MusicBrainzPageEnrichmentService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/music")
class MusicBrainzPageEnrichmentController(
    private val service: MusicBrainzPageEnrichmentService,
) {
    @PostMapping("/musicbrainz/artists/candidates")
    suspend fun newArtistCandidates(
        @RequestParam query: String,
    ) = service.searchNewArtist(query)

    @PostMapping("/musicbrainz/artists")
    suspend fun createArtist(
        @RequestBody request: MusicBrainzArtistCreateRequest,
    ) = service.createArtist(request.artistMbid)

    @PostMapping("/albums/{albumId}/musicbrainz/candidates")
    suspend fun albumCandidates(
        @PathVariable albumId: Long,
    ) = service.searchAlbum(albumId)

    @PostMapping("/albums/{albumId}/musicbrainz/preview")
    suspend fun albumPreview(
        @PathVariable albumId: Long,
        @RequestParam releaseGroupMbid: String,
    ) = service.previewAlbum(albumId, releaseGroupMbid)

    @PostMapping("/albums/{albumId}/musicbrainz")
    suspend fun applyAlbum(
        @PathVariable albumId: Long,
        @RequestBody request: MusicBrainzAlbumApplyRequest,
    ) = service.applyAlbum(albumId, request.releaseGroupMbid)

    @PostMapping("/artists/{artistId}/musicbrainz/candidates")
    suspend fun artistCandidates(
        @PathVariable artistId: Long,
    ) = service.searchArtist(artistId)

    @PostMapping("/artists/{artistId}/musicbrainz")
    suspend fun applyArtist(
        @PathVariable artistId: Long,
        @RequestBody request: MusicBrainzArtistApplyRequest,
    ) = service.applyArtist(artistId, request.artistMbid)

    @PostMapping("/artists/{artistId}/musicbrainz/refresh")
    suspend fun refreshArtist(
        @PathVariable artistId: Long,
    ) = service.refreshArtist(artistId)

    @PostMapping("/artists/{artistId}/musicbrainz/discography")
    suspend fun artistDiscography(
        @PathVariable artistId: Long,
    ) = service.getArtistDiscography(artistId)

    @PostMapping("/artists/{artistId}/musicbrainz/discography/import")
    suspend fun importArtistDiscography(
        @PathVariable artistId: Long,
        @RequestBody request: MusicBrainzDiscographyImportRequest,
    ) = service.importArtistDiscography(artistId, request.releaseGroupMbids)
}
