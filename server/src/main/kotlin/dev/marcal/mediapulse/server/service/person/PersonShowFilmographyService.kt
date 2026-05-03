package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.api.movies.PersonShowFilmographyMemberDto
import dev.marcal.mediapulse.server.api.movies.PersonShowFilmographyResponse
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.movie.MovieCreditType
import dev.marcal.mediapulse.server.repository.crud.PersonRepository
import dev.marcal.mediapulse.server.repository.crud.ShowCreditAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.ShowCreditsCrudRepository
import dev.marcal.mediapulse.server.service.tv.ManualShowCatalogService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class PersonShowFilmographyService(
    private val personRepository: PersonRepository,
    private val showCreditAssignmentRepository: ShowCreditAssignmentRepository,
    private val showCreditsCrudRepository: ShowCreditsCrudRepository,
    private val tmdbApiClient: TmdbApiClient,
    private val manualShowCatalogService: ManualShowCatalogService,
) {
    private val relevantCrewJobs =
        setOf(
            "Director",
            "Writer",
            "Screenplay",
            "Story Editor",
            "Executive Producer",
            "Producer",
            "Original Music Composer",
        )

    @Transactional
    fun fetchFilmography(personId: Long): PersonShowFilmographyResponse {
        val person =
            personRepository.findById(personId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found")
            }

        val filmography =
            tmdbApiClient.fetchPersonTvCredits(person.tmdbId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb person tv filmography not found")

        data class FilmographyItem(
            val tmdbId: String,
            val title: String,
            val originalTitle: String?,
            val overview: String?,
            val year: Int?,
            val posterUrl: String?,
            val backdropUrl: String?,
            val roleLabels: MutableList<String>,
        )

        val merged = linkedMapOf<String, FilmographyItem>()

        filmography.cast
            .sortedBy { it.order ?: Int.MAX_VALUE }
            .forEach { credit ->
                val title = credit.title ?: credit.originalTitle ?: return@forEach
                val item =
                    merged.getOrPut(credit.tmdbId) {
                        FilmographyItem(
                            tmdbId = credit.tmdbId,
                            title = title,
                            originalTitle = credit.originalTitle,
                            overview = credit.overview,
                            year = credit.releaseYear,
                            posterUrl = credit.posterPath?.let(manualShowCatalogService::buildTmdbImageUrl),
                            backdropUrl = credit.backdropPath?.let(manualShowCatalogService::buildTmdbImageUrl),
                            roleLabels = mutableListOf(),
                        )
                    }
                val label = credit.character?.takeIf { it.isNotBlank() } ?: "Elenco"
                if (label !in item.roleLabels) item.roleLabels.add(label)
            }

        filmography.crew
            .filter { it.job in relevantCrewJobs }
            .forEach { credit ->
                val title = credit.title ?: credit.originalTitle ?: return@forEach
                val item =
                    merged.getOrPut(credit.tmdbId) {
                        FilmographyItem(
                            tmdbId = credit.tmdbId,
                            title = title,
                            originalTitle = credit.originalTitle,
                            overview = credit.overview,
                            year = credit.releaseYear,
                            posterUrl = credit.posterPath?.let(manualShowCatalogService::buildTmdbImageUrl),
                            backdropUrl = credit.backdropPath?.let(manualShowCatalogService::buildTmdbImageUrl),
                            roleLabels = mutableListOf(),
                        )
                    }
                val label = credit.job ?: credit.department ?: "Equipe"
                if (label !in item.roleLabels) item.roleLabels.add(label)
            }

        val items =
            merged.values
                .sortedWith(compareByDescending<FilmographyItem> { it.year ?: Int.MIN_VALUE }.thenBy { it.title })
                .toList()

        val localShowsByTmdbId = showCreditsCrudRepository.findLocalShowsByTmdbIds(items.map { it.tmdbId })

        filmography.cast.forEach { credit ->
            val localShow = localShowsByTmdbId[credit.tmdbId] ?: return@forEach
            showCreditAssignmentRepository.upsert(
                ShowCreditAssignmentRepository.UpsertShowCreditRequest(
                    showId = localShow.showId,
                    personId = person.id,
                    creditType = MovieCreditType.CAST,
                    characterName = credit.character ?: "",
                    billingOrder = credit.order,
                ),
            )
        }

        filmography.crew
            .filter { it.job in relevantCrewJobs }
            .forEach { credit ->
                val localShow = localShowsByTmdbId[credit.tmdbId] ?: return@forEach
                showCreditAssignmentRepository.upsert(
                    ShowCreditAssignmentRepository.UpsertShowCreditRequest(
                        showId = localShow.showId,
                        personId = person.id,
                        creditType = MovieCreditType.CREW,
                        department = credit.department ?: "",
                        job = credit.job ?: "",
                    ),
                )
            }

        return PersonShowFilmographyResponse(
            personId = person.id,
            tmdbId = person.tmdbId,
            name = person.name,
            profileUrl = person.profileUrl,
            members =
                items.map { item ->
                    val localShow = localShowsByTmdbId[item.tmdbId]
                    PersonShowFilmographyMemberDto(
                        tmdbId = item.tmdbId,
                        title = item.title,
                        originalTitle = item.originalTitle,
                        year = item.year,
                        overview = item.overview,
                        posterUrl = item.posterUrl,
                        backdropUrl = item.backdropUrl,
                        tmdbUrl = "https://www.themoviedb.org/tv/${item.tmdbId}",
                        localShowId = localShow?.showId,
                        localSlug = localShow?.slug,
                        inCatalog = localShow != null,
                        roleLabel = item.roleLabels.take(3).joinToString(" · "),
                    )
                },
        )
    }
}
