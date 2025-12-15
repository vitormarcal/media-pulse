package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Genre
import dev.marcal.mediapulse.server.model.music.GenreSource
import dev.marcal.mediapulse.server.repository.crud.AlbumGenreRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumGenreSourceRepository
import dev.marcal.mediapulse.server.repository.crud.GenreRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AlbumGenreService(
    private val genreRepository: GenreRepository,
    private val albumGenreRepository: AlbumGenreRepository,
    private val albumGenreSourceRepository: AlbumGenreSourceRepository,
) {
    @Transactional
    suspend fun addGenres(
        album: Album,
        genres: Collection<String>?,
        source: GenreSource,
    ) {
        if (genres.isNullOrEmpty()) return

        val normalized =
            genres
                .map { it.lowercase().trim() }
                .filter { it.isNotBlank() }
                .distinct()

        if (normalized.isEmpty()) return

        val savedGenres =
            normalized.map { name ->
                genreRepository.findByName(name)
                    ?: genreRepository.save(Genre(name = name))
            }

        val genreIds = savedGenres.map { it.id }

        // 1) Estado final: album_genres
        val existing = albumGenreRepository.findGenreIdsByAlbum(album.id)
        val toInsert = genreIds.filterNot { it in existing }
        albumGenreRepository.insert(album.id, toInsert)

        // 2) Proveniência: album_genre_sources
        // registra todos os gêneros processados (mesmo se já existiam no album_genres)
        albumGenreSourceRepository.insert(album.id, genreIds, source)
    }
}
