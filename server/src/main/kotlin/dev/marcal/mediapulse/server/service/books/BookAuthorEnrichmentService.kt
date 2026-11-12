package dev.marcal.mediapulse.server.service.books

import com.fasterxml.jackson.databind.JsonNode
import dev.marcal.mediapulse.server.api.books.AuthorEnrichmentApply
import dev.marcal.mediapulse.server.api.books.AuthorEnrichmentDto
import dev.marcal.mediapulse.server.api.books.AuthorEnrichmentField
import dev.marcal.mediapulse.server.api.books.AuthorEnrichmentPreview
import dev.marcal.mediapulse.server.integration.hardcover.HardcoverApiClient
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverAuthorProfile
import dev.marcal.mediapulse.server.repository.BookAuthorProfileRepository
import dev.marcal.mediapulse.server.service.image.LibraryImageStorageService
import dev.marcal.mediapulse.server.util.TxUtil
import kotlinx.coroutines.CancellationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class BookAuthorEnrichmentService(
    private val profiles: BookAuthorProfileRepository,
    private val hardcover: HardcoverApiClient,
    private val images: LibraryImageStorageService,
    private val tx: TxUtil,
) {
    private data class Pending(
        val authorId: Long,
        val preview: AuthorEnrichmentPreview,
    )

    private val pending = ConcurrentHashMap<String, Pending>()

    suspend fun preview(
        authorId: Long,
        reference: String,
    ): AuthorEnrichmentPreview {
        if (!profiles.exists(authorId)) fail(HttpStatus.NOT_FOUND, "Autor não encontrado.")
        val (id, slug) = parseReference(reference)
        val remote =
            try {
                hardcover.fetchAuthorProfile(id, slug)
            } catch (ex: CancellationException) {
                throw ex
            } catch (_: Exception) {
                fail(HttpStatus.BAD_GATEWAY, "Não foi possível consultar o Hardcover. Tente novamente.")
            } ?: fail(HttpStatus.NOT_FOUND, "Perfil não encontrado no Hardcover.")
        if (remote.id <= 0 || remote.name.isBlank()) fail(HttpStatus.BAD_GATEWAY, "Perfil inválido no Hardcover.")
        if (profiles.owner(remote.id)?.let { it != authorId } ==
            true
        ) {
            fail(HttpStatus.CONFLICT, "Este perfil já está vinculado a outro autor local. Revise os autores antes de associar.")
        }
        val now = Instant.now()
        val proposal = mapProfile(remote, now)
        val result = AuthorEnrichmentPreview(UUID.randomUUID().toString(), profiles.find(authorId), proposal, now.plusSeconds(900))
        synchronized(pending) {
            pending.entries.removeIf {
                !it.value.preview.expiresAt
                    .isAfter(now)
            }
            if (pending.size >= 100) pending.keys.firstOrNull()?.let { pending.remove(it) }
            pending[result.token] = Pending(authorId, result)
        }
        return result
    }

    fun apply(
        authorId: Long,
        request: AuthorEnrichmentApply,
    ): AuthorEnrichmentDto {
        val snapshot =
            pending[request.token]?.takeIf { it.authorId == authorId }
                ?: fail(HttpStatus.CONFLICT, "Prévia expirada. Consulte novamente.")
        synchronized(snapshot) {
            if (pending[request.token] !== snapshot ||
                !snapshot.preview.expiresAt.isAfter(Instant.now())
            ) {
                fail(HttpStatus.CONFLICT, "Prévia expirada. Consulte novamente.")
            }
            val preview = snapshot.preview
            val proposed = preview.proposed
            if (request.fields.isEmpty()) fail(HttpStatus.BAD_REQUEST, "Selecione ao menos um campo disponível.")
            val changing = preview.current != null && preview.current.hardcoverId != proposed.hardcoverId
            if (changing &&
                !request.confirmIdentityChange
            ) {
                fail(HttpStatus.CONFLICT, "Confirme a troca de perfil; os dados do perfil anterior serão removidos.")
            }
            if (profiles.find(authorId) != preview.current) fail(HttpStatus.CONFLICT, "O perfil mudou. Consulte uma nova prévia.")
            var result =
                if (changing ||
                    preview.current == null
                ) {
                    proposed.copy(
                        biography = null,
                        born = null,
                        died = null,
                        location = null,
                        links = emptyList(),
                        photoUrl = null,
                        photoSourceUrl = null,
                    )
                } else {
                    preview.current.copy(
                        externalName = proposed.externalName,
                        sourceUrl = proposed.sourceUrl,
                        fetchedAt = proposed.fetchedAt,
                    )
                }
            for (field in request.fields) {
                result =
                    when (field) {
                        AuthorEnrichmentField.BIOGRAPHY -> {
                            requireAvailable(proposed.biography != null)
                            result.copy(biography = proposed.biography)
                        }
                        AuthorEnrichmentField.DATES -> {
                            requireAvailable(proposed.born != null || proposed.died != null)
                            result.copy(
                                born =
                                    proposed.born ?: result.born,
                                died = proposed.died ?: result.died,
                            )
                        }
                        AuthorEnrichmentField.LOCATION -> {
                            requireAvailable(proposed.location != null)
                            result.copy(location = proposed.location)
                        }
                        AuthorEnrichmentField.LINKS -> {
                            requireAvailable(proposed.links.isNotEmpty())
                            result.copy(links = proposed.links)
                        }
                        AuthorEnrichmentField.PHOTO -> {
                            requireAvailable(proposed.photoSourceUrl != null)
                            val stored =
                                try {
                                    images.saveBookAuthor(
                                        images.downloadProviderImage(proposed.photoSourceUrl!!),
                                        authorId,
                                        proposed.externalName,
                                    )
                                } catch (
                                    _: Exception,
                                ) {
                                    fail(
                                        HttpStatus.BAD_GATEWAY,
                                        "Não foi possível salvar a foto. Nada foi aplicado. Tente novamente ou desmarque a foto para aplicar os outros campos.",
                                    )
                                }
                            result.copy(photoUrl = stored, photoSourceUrl = proposed.photoSourceUrl)
                        }
                    }
            }
            try {
                tx.inTx {
                    if (!preview.expiresAt.isAfter(Instant.now())) fail(HttpStatus.CONFLICT, "Prévia expirada. Consulte novamente.")
                    if (!profiles.lock(authorId)) fail(HttpStatus.NOT_FOUND, "Autor não encontrado.")
                    if (profiles.find(authorId) != preview.current) fail(HttpStatus.CONFLICT, "O perfil mudou. Consulte uma nova prévia.")
                    if (profiles.owner(proposed.hardcoverId)?.let { it != authorId } ==
                        true
                    ) {
                        fail(HttpStatus.CONFLICT, "Este perfil já está vinculado a outro autor local.")
                    }
                    profiles.save(authorId, result)
                }
            } catch (
                _: DataIntegrityViolationException,
            ) {
                fail(HttpStatus.CONFLICT, "Este perfil já está vinculado. Consulte novamente e revise os autores.")
            }
            pending.remove(request.token, snapshot)
            return result
        }
    }

    private fun requireAvailable(available: Boolean) {
        if (!available) fail(HttpStatus.BAD_REQUEST, "Campo indisponível na prévia. Revise a seleção.")
    }

    companion object {
        internal fun mapProfile(
            remote: HardcoverAuthorProfile,
            now: Instant = Instant.now(),
        ): AuthorEnrichmentDto {
            val photo = remote.image?.url?.takeIf(::allowedPhoto)
            return AuthorEnrichmentDto(
                hardcoverId = remote.id,
                externalName = remote.name,
                sourceUrl = "https://hardcover.app/authors/${remote.slug?.takeIf { it.matches(Regex("[a-zA-Z0-9_-]+")) } ?: remote.id}",
                fetchedAt = now,
                biography = remote.bio?.trim()?.takeIf(String::isNotEmpty),
                born = date(remote.bornDate, remote.bornYear),
                died = date(remote.deathDate, remote.deathYear),
                location = remote.location?.trim()?.takeIf(String::isNotEmpty),
                links = collectLinks(remote.links),
                photoUrl = photo,
                photoSourceUrl = photo,
            )
        }

        internal fun parseReference(reference: String): Pair<Long?, String?> {
            val value = reference.trim()
            value.toLongOrNull()?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.let { return it to null }
            val uri = runCatching { URI(value) }.getOrNull()
            val slug = uri?.path?.let { Regex("^/authors/([a-zA-Z0-9_-]+)/?$").matchEntire(it)?.groupValues?.get(1) }
            if (uri?.scheme != "https" ||
                uri.host !in setOf("hardcover.app", "www.hardcover.app") ||
                uri.userInfo != null ||
                uri.port != -1 ||
                uri.query != null ||
                uri.fragment != null ||
                slug == null
            ) {
                fail(HttpStatus.BAD_REQUEST, "Informe um ID positivo ou link https://hardcover.app/authors/…")
            }
            return null to slug
        }

        internal fun allowedPhoto(value: String): Boolean =
            runCatching {
                URI(value).let {
                    it.scheme == "https" &&
                        it.host in setOf("images.hardcover.app", "assets.hardcover.app") &&
                        it.userInfo == null &&
                        it.port == -1
                }
            }.getOrDefault(false)

        private fun date(
            value: String?,
            year: Int?,
        ): String? =
            value?.let { runCatching { LocalDate.parse(it).toString() }.getOrNull() }
                ?: year?.takeIf { it in 1..9999 }?.toString()?.padStart(4, '0')

        internal fun collectLinks(node: JsonNode?): List<String> {
            if (node == null) return emptyList()
            val values =
                if (node.isTextual) {
                    listOf(
                        node.asText(),
                    )
                } else {
                    node
                        .elements()
                        .asSequence()
                        .flatMap { collectLinks(it).asSequence() }
                        .toList()
                }
            return values
                .filter {
                    runCatching {
                        URI(it).let { u ->
                            u.scheme in setOf("https", "http") &&
                                !u.host.isNullOrBlank() &&
                                u.userInfo == null
                        }
                    }.getOrDefault(false)
                }.distinct()
                .take(20)
        }

        private fun fail(
            status: HttpStatus,
            message: String,
        ): Nothing = throw ResponseStatusException(status, message)
    }
}
