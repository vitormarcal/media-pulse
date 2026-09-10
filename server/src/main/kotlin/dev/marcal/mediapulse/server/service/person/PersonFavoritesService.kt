package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.api.movies.PersonFavoriteDto
import dev.marcal.mediapulse.server.repository.PersonFavoritesRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PersonFavoritesService(
    private val repository: PersonFavoritesRepository,
) {
    @Transactional(readOnly = true)
    fun list(): List<PersonFavoriteDto> = repository.findAll()

    @Transactional
    fun favorite(personId: Long) = repository.setFavorite(personId, true)

    @Transactional
    fun unfavorite(personId: Long) = repository.setFavorite(personId, false)
}
