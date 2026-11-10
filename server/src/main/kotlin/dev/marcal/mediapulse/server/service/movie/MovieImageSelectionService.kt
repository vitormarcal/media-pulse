package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieImageCandidateDto
import dev.marcal.mediapulse.server.api.movies.MovieImageCandidatesResponse
import dev.marcal.mediapulse.server.api.movies.MovieImageLookupResponse
import dev.marcal.mediapulse.server.api.movies.MovieImageLookupResult
import dev.marcal.mediapulse.server.api.movies.MovieImageSelectionRequest
import dev.marcal.mediapulse.server.api.movies.MovieImageSelectionResponse
import dev.marcal.mediapulse.server.config.TmdbProperties
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.repository.crud.MovieImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.service.image.LibraryImageStorageService
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class MovieImageSelectionService(
    private val movies: MovieRepository,
    private val images: MovieImageCrudRepository,
    private val tmdb: TmdbApiClient,
    private val properties: TmdbProperties,
    private val storage: LibraryImageStorageService,
    private val assignment: MovieImageAssignmentService,
    private val library: VisualLibraryClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun candidates(movieId: Long): MovieImageCandidatesResponse {
        val movie = movie(movieId)
        if (movie.tmdbId.isNullOrBlank()) {
            return MovieImageCandidatesResponse(emptyList(), listOf("Vincule o filme ao TMDb em Atualizar dados."))
        }
        return MovieImageCandidatesResponse(options(movie))
    }

    fun save(
        movieId: Long,
        request: MovieImageSelectionRequest,
    ): MovieImageSelectionResponse {
        val ids = validatedIds(request, 50)
        val movie = movie(movieId)
        val options = options(movie).associateBy { it.id }
        val saved = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for (id in ids) {
            val option = options[id]
            if (option == null) {
                failed += id
                continue
            }
            try {
                val content = storage.downloadProviderImage(option.url)
                val url = storage.saveMovie(content, movieId, movie.originalTitle, "TMDB", id.take(24))
                assignment.append(movieId, url)
                saved += id
            } catch (error: Exception) {
                logger.warn("Falha ao guardar imagem do TMDb. movieId={} error={}", movieId, error.javaClass.simpleName)
                failed += id
            }
        }
        return MovieImageSelectionResponse(saved, failed)
    }

    fun lookup(
        movieId: Long,
        request: MovieImageSelectionRequest,
    ): MovieImageLookupResponse {
        val ids = validatedIds(request, 8)
        val movie = movie(movieId)

        fun unavailable() = MovieImageLookupResponse(false, ids.map { MovieImageLookupResult(it, "UNAVAILABLE") })
        if (!library.enabled) return unavailable()

        val available = options(movie).associateBy { it.id }
        val hashes =
            ids
                .mapNotNull { id ->
                    val option = available[id] ?: return@mapNotNull null
                    runCatching {
                        val bytes = storage.downloadProviderImage(option.url).bytes
                        require(bytes.isNotEmpty() && bytes.size <= LibraryImageStorageService.MAX_BYTES)
                        id to DigestUtils.sha256Hex(bytes)
                    }.getOrNull()
                }.toMap()
        val matches =
            try {
                if (hashes.isEmpty()) emptyMap() else library.lookupHashes(hashes.values.distinct())
            } catch (_: Exception) {
                return unavailable()
            }
        val linked =
            (images.findByMovieIdOrderByIsPrimaryDescIdDesc(movieId).map { it.url } + listOfNotNull(movie.coverUrl))
                .mapNotNull { VisualLibraryClient.assetId(it) }
                .toSet()
        return MovieImageLookupResponse(
            true,
            ids.map { id ->
                val hash = hashes[id]
                val asset = hash?.let(matches::get)
                val state =
                    when {
                        hash == null || !matches.containsKey(hash) -> "UNAVAILABLE"
                        asset == null -> "NOT_FOUND"
                        asset in linked -> "IN_GALLERY"
                        else -> "IN_LIBRARY"
                    }
                MovieImageLookupResult(id, state)
            },
        )
    }

    private fun options(movie: Movie): List<MovieImageCandidateDto> {
        val tmdbId =
            movie.tmdbId?.takeIf { it.matches(Regex("[0-9]+")) }
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Vincule o filme ao TMDb em Atualizar dados.")
        if (!properties.enabled) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TMDb não configurado.")
        val result =
            try {
                tmdb.fetchMovieImages(tmdbId)
            } catch (_: Exception) {
                throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível buscar imagens no TMDb.")
            }
        val root = properties.imageBaseUrl.trimEnd('/') + "/t/p"
        return listOf("POSTER" to result.posters, "BACKDROP" to result.backdrops)
            .flatMap { (kind, entries) ->
                entries.mapNotNull { image ->
                    val path = image.filePath?.takeIf { it.matches(imagePath) } ?: return@mapNotNull null
                    MovieImageCandidateDto(
                        id = DigestUtils.sha256Hex("${movie.id}:$tmdbId:$path"),
                        kind = kind,
                        url = "$root/original$path",
                        previewUrl = "$root/w780$path",
                        width = image.width,
                        height = image.height,
                    )
                }
            }.distinctBy { it.id }
    }

    private fun validatedIds(
        request: MovieImageSelectionRequest,
        limit: Int,
    ): List<String> {
        if (request.ids.size !in 1..limit || request.ids.any { it == null || !it.matches(candidateId) }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione entre uma e $limit imagens disponíveis.")
        }
        return request.ids.filterNotNull().distinct()
    }

    private fun movie(id: Long): Movie =
        movies.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Filme não encontrado.") }

    companion object {
        private val imagePath = Regex("/[A-Za-z0-9_-]+\\.(jpg|jpeg|png|webp)", RegexOption.IGNORE_CASE)
        private val candidateId = Regex("[a-f0-9]{64}")
    }
}
