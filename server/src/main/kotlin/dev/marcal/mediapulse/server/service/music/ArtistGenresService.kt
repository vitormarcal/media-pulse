package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.api.music.AlbumTermCreateRequest
import dev.marcal.mediapulse.server.api.music.AlbumTermDto
import dev.marcal.mediapulse.server.api.music.AlbumTermKindDto
import dev.marcal.mediapulse.server.model.music.AlbumTerm
import dev.marcal.mediapulse.server.model.music.AlbumTermKind
import dev.marcal.mediapulse.server.model.music.AlbumTermSource
import dev.marcal.mediapulse.server.repository.ArtistProfileRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumTermRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.util.SlugTextUtil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ArtistGenresService(
    private val artists: ArtistRepository,
    private val terms: AlbumTermRepository,
    private val profiles: ArtistProfileRepository,
) {
    fun addGenre(
        artistId: Long,
        request: AlbumTermCreateRequest,
    ): AlbumTermDto {
        if (request.kind != AlbumTermKindDto.GENRE) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Artist terms must be genres")
        requireArtist(artistId)
        val term = upsert(request.name, AlbumTermSource.USER)
        profiles.upsertGenre(artistId, term.id, AlbumTermSource.USER.name, restore = true)
        return profiles.genres(artistId).first { it.id == term.id }
    }

    fun addMusicBrainzGenres(
        artistId: Long,
        names: Collection<String>,
    ) {
        requireArtist(artistId)
        names.forEach { name ->
            val term = upsert(name, AlbumTermSource.MUSICBRAINZ)
            profiles.upsertGenre(artistId, term.id, AlbumTermSource.MUSICBRAINZ.name)
        }
    }

    fun updateVisibility(
        artistId: Long,
        termId: Long,
        hidden: Boolean,
    ): AlbumTermDto {
        requireArtist(artistId)
        if (profiles.updateGenreVisibility(artistId, termId, hidden) == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Artist genre not found")
        }
        return profiles.genres(artistId).first { it.id == termId }
    }

    private fun upsert(
        rawName: String,
        source: AlbumTermSource,
    ): AlbumTerm {
        val name = rawName.trim().replace("\\s+".toRegex(), " ")
        if (name.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "name deve ser preenchido")
        val normalized = name.lowercase()
        val existing = terms.findByKindAndNormalizedName(AlbumTermKind.GENRE, normalized)
        return existing ?: terms.save(
            AlbumTerm(
                name = name,
                normalizedName = normalized,
                slug = SlugTextUtil.normalize(name, 64),
                kind = AlbumTermKind.GENRE,
                source = source,
            ),
        )
    }

    private fun requireArtist(artistId: Long) =
        artists.findById(artistId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Artist not found")
        }
}
