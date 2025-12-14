package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Genre
import dev.marcal.mediapulse.server.repository.crud.AlbumGenreRepository
import dev.marcal.mediapulse.server.repository.crud.GenreRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AlbumGenreService(
    private val genreRepository: GenreRepository,
    private val albumGenreRepository: AlbumGenreRepository,
) {
    @Transactional
    suspend fun addGenres(
        album: Album,
        genres: Collection<String>?,
    ) {
        if (genres.isNullOrEmpty()) {
            return
        }

        val normalized =
            genres
                .map { it.lowercase().trim() }
                .filter { it.isNotBlank() }
                .distinct()

        if (normalized.isEmpty()) return

        val genresNames =
            normalized.map { name ->
                genreRepository.findByName(name)
                    ?: genreRepository.save(Genre(name = name))
            }

        val existing = albumGenreRepository.findGenreIdsByAlbum(album.id)

        val toInsert =
            genresNames
                .map { it.id }
                .filterNot { it in existing }

        if (toInsert.isEmpty()) return

        albumGenreRepository.insert(album.id, toInsert)
    }
}
