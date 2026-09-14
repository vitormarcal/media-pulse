package dev.marcal.mediapulse.server.controller.people

import dev.marcal.mediapulse.server.api.movies.PersonDetailsResponse
import dev.marcal.mediapulse.server.api.movies.PersonFavoriteDto
import dev.marcal.mediapulse.server.api.movies.PersonFilmographyResponse
import dev.marcal.mediapulse.server.api.movies.PersonShowFilmographyResponse
import dev.marcal.mediapulse.server.api.movies.PersonSuggestionDto
import dev.marcal.mediapulse.server.api.people.PersonHistoryCategory
import dev.marcal.mediapulse.server.api.people.PersonHistoryPageResponse
import dev.marcal.mediapulse.server.service.movie.MovieCreditsService
import dev.marcal.mediapulse.server.service.person.PersonDetailsService
import dev.marcal.mediapulse.server.service.person.PersonFavoritesService
import dev.marcal.mediapulse.server.service.person.PersonFilmographyService
import dev.marcal.mediapulse.server.service.person.PersonHistoryService
import dev.marcal.mediapulse.server.service.person.PersonShowFilmographyService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/people")
class PeopleController(
    private val personDetailsService: PersonDetailsService,
    private val movieCreditsService: MovieCreditsService,
    private val personFilmographyService: PersonFilmographyService,
    private val personShowFilmographyService: PersonShowFilmographyService,
    private val personFavoritesService: PersonFavoritesService,
    private val personHistoryService: PersonHistoryService,
) {
    @GetMapping("/favorites")
    fun favorites(): List<PersonFavoriteDto> = personFavoritesService.list()

    @GetMapping("/history/most-present")
    fun mostPresentInHistory(
        @RequestParam category: PersonHistoryCategory,
        @RequestParam(defaultValue = "4") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): PersonHistoryPageResponse = personHistoryService.mostPresent(category, limit, offset)

    @PostMapping("/{personId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun favorite(
        @PathVariable personId: Long,
    ) = personFavoritesService.favorite(personId)

    @DeleteMapping("/{personId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unfavorite(
        @PathVariable personId: Long,
    ) = personFavoritesService.unfavorite(personId)

    @GetMapping("/{slug}")
    fun details(
        @PathVariable slug: String,
    ): PersonDetailsResponse = personDetailsService.fetchLocalDetails(slug)

    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "8") limit: Int,
    ): List<PersonSuggestionDto> = movieCreditsService.searchPeople(q, limit.coerceIn(1, 1000))

    @GetMapping("/{personId}/filmography")
    fun filmography(
        @PathVariable personId: Long,
    ): PersonFilmographyResponse = personFilmographyService.getFilmography(personId)

    @GetMapping("/{personId}/show-filmography")
    fun showFilmography(
        @PathVariable personId: Long,
    ): PersonShowFilmographyResponse = personShowFilmographyService.getFilmography(personId)
}
