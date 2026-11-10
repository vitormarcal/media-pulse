package dev.marcal.mediapulse.server.service.image

import dev.marcal.mediapulse.server.api.image.TmdbImageCandidateDto
import dev.marcal.mediapulse.server.api.image.TmdbImageCandidatesResponse
import dev.marcal.mediapulse.server.api.image.TmdbImageLookupResponse
import dev.marcal.mediapulse.server.api.image.TmdbImageLookupResult
import dev.marcal.mediapulse.server.api.image.TmdbImageSelectionRequest
import dev.marcal.mediapulse.server.api.image.TmdbImageSelectionResponse
import dev.marcal.mediapulse.server.config.TmdbProperties
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class TmdbImageSelectionService(
    private val tmdb: TmdbApiClient,
    private val properties: TmdbProperties,
    private val storage: LibraryImageStorageService,
    private val library: VisualLibraryClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    enum class Kind(
        val linkGuidance: String,
    ) {
        MOVIE("Vincule o filme ao TMDb em Atualizar dados."),
        SHOW("Vincule a série ao TMDb em Atualizar dados."),
    }

    data class Subject(
        val kind: Kind,
        val id: Long,
        val title: String,
        val tmdbId: String?,
    )

    fun candidates(subject: Subject): TmdbImageCandidatesResponse {
        if (subject.tmdbId.isNullOrBlank()) {
            return TmdbImageCandidatesResponse(emptyList(), listOf(subject.kind.linkGuidance))
        }
        return TmdbImageCandidatesResponse(options(subject))
    }

    fun save(
        subject: Subject,
        request: TmdbImageSelectionRequest,
        append: (String) -> Unit,
    ): TmdbImageSelectionResponse {
        val ids = validatedIds(request, 50)
        val options = options(subject).associateBy { it.id }
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
                val fileHint = id.take(24)
                val url =
                    when (subject.kind) {
                        Kind.MOVIE -> storage.saveMovie(content, subject.id, subject.title, "TMDB", fileHint)
                        Kind.SHOW -> storage.saveShow(content, subject.id, subject.title, "TMDB", fileHint)
                    }
                append(url)
                saved += id
            } catch (error: Exception) {
                logger.warn("Falha ao guardar imagem do TMDb. kind={} id={} error={}", subject.kind, subject.id, error.javaClass.simpleName)
                failed += id
            }
        }
        return TmdbImageSelectionResponse(saved, failed)
    }

    fun lookup(
        subject: Subject,
        request: TmdbImageSelectionRequest,
        linkedUrls: () -> List<String>,
    ): TmdbImageLookupResponse {
        val ids = validatedIds(request, 8)

        fun unavailable() = TmdbImageLookupResponse(false, ids.map { TmdbImageLookupResult(it, "UNAVAILABLE") })
        if (!library.enabled) return unavailable()

        val available = options(subject).associateBy { it.id }
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
        val linked = linkedUrls().mapNotNull { VisualLibraryClient.assetId(it) }.toSet()
        return TmdbImageLookupResponse(
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
                TmdbImageLookupResult(id, state)
            },
        )
    }

    private fun options(subject: Subject): List<TmdbImageCandidateDto> {
        val tmdbId =
            subject.tmdbId?.takeIf { it.matches(Regex("[0-9]+")) }
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, subject.kind.linkGuidance)
        if (!properties.enabled) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TMDb não configurado.")
        val result =
            try {
                when (subject.kind) {
                    Kind.MOVIE -> tmdb.fetchMovieImages(tmdbId)
                    Kind.SHOW -> tmdb.fetchShowImages(tmdbId)
                }
            } catch (_: Exception) {
                throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível buscar imagens no TMDb.")
            }
        val scope = if (subject.kind == Kind.MOVIE) subject.id.toString() else "show:${subject.id}"
        val root = properties.imageBaseUrl.trimEnd('/') + "/t/p"
        return listOf("POSTER" to result.posters, "BACKDROP" to result.backdrops)
            .flatMap { (kind, entries) ->
                entries.mapNotNull { image ->
                    val path = image.filePath?.takeIf { it.matches(imagePath) } ?: return@mapNotNull null
                    TmdbImageCandidateDto(
                        id = DigestUtils.sha256Hex("$scope:$tmdbId:$path"),
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
        request: TmdbImageSelectionRequest,
        limit: Int,
    ): List<String> {
        if (request.ids.size !in 1..limit || request.ids.any { it == null || !it.matches(candidateId) }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione entre uma e $limit imagens disponíveis.")
        }
        return request.ids.filterNotNull().distinct()
    }

    companion object {
        private val imagePath = Regex("/[A-Za-z0-9_-]+\\.(jpg|jpeg|png|webp)", RegexOption.IGNORE_CASE)
        private val candidateId = Regex("[a-f0-9]{64}")
    }
}
