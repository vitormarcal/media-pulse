package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.api.movies.PersonDetailsResponse
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PersonDetailsService(
    private val repository: MovieQueryRepository,
) {
    @Transactional(readOnly = true)
    fun fetchLocalDetails(slug: String): PersonDetailsResponse = repository.getPersonDetails(slug)
}
