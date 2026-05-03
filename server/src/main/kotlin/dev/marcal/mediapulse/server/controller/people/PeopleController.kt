package dev.marcal.mediapulse.server.controller.people

import dev.marcal.mediapulse.server.api.movies.PersonDetailsResponse
import dev.marcal.mediapulse.server.api.movies.PersonFilmographyResponse
import dev.marcal.mediapulse.server.api.movies.PersonShowFilmographyResponse
import dev.marcal.mediapulse.server.api.movies.PersonSuggestionDto
import dev.marcal.mediapulse.server.service.movie.MovieCreditsService
import dev.marcal.mediapulse.server.service.person.PersonDetailsService
import dev.marcal.mediapulse.server.service.person.PersonFilmographyService
import dev.marcal.mediapulse.server.service.person.PersonShowFilmographyService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/people")
class PeopleController(
    private val personDetailsService: PersonDetailsService,
    private val movieCreditsService: MovieCreditsService,
    private val personFilmographyService: PersonFilmographyService,
    private val personShowFilmographyService: PersonShowFilmographyService,
) {
    @GetMapping("/{slug}")
    fun details(
        @PathVariable slug: String,
    ): PersonDetailsResponse = personDetailsService.fetchDetails(slug)

    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "8") limit: Int,
    ): List<PersonSuggestionDto> = movieCreditsService.searchPeople(q, limit.coerceIn(1, 1000))

    @GetMapping("/{personId}/tmdb-filmography")
    fun tmdbFilmography(
        @PathVariable personId: Long,
    ): PersonFilmographyResponse = personFilmographyService.fetchFilmography(personId)

    @GetMapping("/{personId}/tmdb-show-filmography")
    fun tmdbShowFilmography(
        @PathVariable personId: Long,
    ): PersonShowFilmographyResponse = personShowFilmographyService.fetchFilmography(personId)
}
