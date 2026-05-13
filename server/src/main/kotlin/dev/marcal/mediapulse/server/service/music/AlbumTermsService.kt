package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.api.music.AlbumTermCreateRequest
import dev.marcal.mediapulse.server.api.music.AlbumTermDto
import dev.marcal.mediapulse.server.api.music.AlbumTermKindDto
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.AlbumTerm
import dev.marcal.mediapulse.server.model.music.AlbumTermKind
import dev.marcal.mediapulse.server.model.music.AlbumTermSource
import dev.marcal.mediapulse.server.repository.MusicQueryRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumTermAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumTermRepository
import dev.marcal.mediapulse.server.util.SlugTextUtil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class AlbumTermsService(
    private val albumRepository: AlbumRepository,
    private val musicQueryRepository: MusicQueryRepository,
    private val albumTermRepository: AlbumTermRepository,
    private val albumTermAssignmentRepository: AlbumTermAssignmentRepository,
) {
    @Transactional
    fun addTerm(
        albumId: Long,
        request: AlbumTermCreateRequest,
    ): AlbumTermDto {
        val album = requireAlbum(albumId)
        val term = upsertTermAndAssignment(album, request.kind.toModel(), request.name, AlbumTermSource.USER)
        return musicQueryRepository.getAlbumTerms(albumId).first { it.id == term.id }
    }

    @Transactional
    fun updateGlobalVisibility(
        termId: Long,
        hidden: Boolean,
    ): AlbumTermDto {
        val term =
            albumTermRepository.findById(termId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Album term not found")
            }

        val saved = albumTermRepository.save(term.copy(hidden = hidden, updatedAt = Instant.now()))
        return musicQueryRepository.findAlbumTerm(saved.id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Album term not found")
    }

    @Transactional
    fun updateAlbumVisibility(
        albumId: Long,
        termId: Long,
        hidden: Boolean,
    ): AlbumTermDto {
        requireAlbum(albumId)
        val updated = albumTermAssignmentRepository.updateVisibility(albumId, termId, hidden)
        if (updated == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Album term assignment not found")
        }
        return musicQueryRepository.getAlbumTerms(albumId).firstOrNull { it.id == termId }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Album term not found for album")
    }

    private fun requireAlbum(albumId: Long): Album =
        albumRepository.findById(albumId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found")
        }

    private fun upsertTermAndAssignment(
        album: Album,
        kind: AlbumTermKind,
        rawName: String,
        assignmentSource: AlbumTermSource,
    ): AlbumTerm {
        val name = rawName.trim().replace("\\s+".toRegex(), " ")
        if (name.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "name deve ser preenchido")
        }

        val normalizedName = name.lowercase()
        val existing = albumTermRepository.findByKindAndNormalizedName(kind, normalizedName)
        val term =
            existing
                ?: albumTermRepository.save(
                    AlbumTerm(
                        name = name,
                        normalizedName = normalizedName,
                        slug = SlugTextUtil.normalize(name, maxLength = 64),
                        kind = kind,
                        source = assignmentSource,
                    ),
                )

        val refreshedTerm =
            if (existing != null && existing.hidden) {
                albumTermRepository.save(existing.copy(hidden = false, updatedAt = Instant.now()))
            } else {
                term
            }

        albumTermAssignmentRepository.upsert(album.id, refreshedTerm.id, assignmentSource)
        return refreshedTerm
    }

    private fun AlbumTermKindDto.toModel(): AlbumTermKind = AlbumTermKind.valueOf(name)
}
