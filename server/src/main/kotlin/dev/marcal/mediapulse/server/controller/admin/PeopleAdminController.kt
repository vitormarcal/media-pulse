package dev.marcal.mediapulse.server.controller.admin

import dev.marcal.mediapulse.server.api.movies.PersonFilmographyResponse
import dev.marcal.mediapulse.server.api.movies.PersonShowFilmographyResponse
import dev.marcal.mediapulse.server.service.person.PersonFilmographyService
import dev.marcal.mediapulse.server.service.person.PersonShowFilmographyService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/people")
class PeopleAdminController(
    private val movieFilmographyService: PersonFilmographyService,
    private val showFilmographyService: PersonShowFilmographyService,
) {
    @PostMapping("/{personId}/tmdb-filmography")
    fun refreshMovieFilmography(
        @PathVariable personId: Long,
    ): PersonFilmographyResponse = movieFilmographyService.refreshAndGetFilmography(personId)

    @PostMapping("/{personId}/tmdb-show-filmography")
    fun refreshShowFilmography(
        @PathVariable personId: Long,
    ): PersonShowFilmographyResponse = showFilmographyService.refreshAndGetFilmography(personId)
}
