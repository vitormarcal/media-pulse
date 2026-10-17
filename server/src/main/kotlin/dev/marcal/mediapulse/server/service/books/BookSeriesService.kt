package dev.marcal.mediapulse.server.service.books

import dev.marcal.mediapulse.server.api.books.ApplyBookSeriesSuggestionRequest
import dev.marcal.mediapulse.server.api.books.BookGroupDetailsDto
import dev.marcal.mediapulse.server.api.books.BookGroupDto
import dev.marcal.mediapulse.server.api.books.BookSeriesSuggestionDto
import dev.marcal.mediapulse.server.model.book.BookGroupKind
import dev.marcal.mediapulse.server.repository.BookSeriesRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
@Transactional
class BookSeriesService(
    private val repository: BookSeriesRepository,
) {
    fun kind(value: String): BookGroupKind =
        when (value) {
            "series" -> BookGroupKind.SERIES
            "universes" -> BookGroupKind.UNIVERSES
            else -> throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }

    @Transactional(readOnly = true)
    fun list(kind: BookGroupKind) = repository.list(kind)

    @Transactional(readOnly = true)
    fun details(
        kind: BookGroupKind,
        id: Long,
    ): BookGroupDetailsDto =
        BookGroupDetailsDto(
            id,
            requireGroup(kind, id),
            repository.items(kind, id),
            if (kind ==
                BookGroupKind.SERIES
            ) {
                repository.universes(id)
            } else {
                emptyList()
            },
        )

    @Transactional(readOnly = true)
    fun forBook(id: Long): List<BookGroupDto> {
        requireBook(id)
        return repository.parents(id)
    }

    fun create(
        kind: BookGroupKind,
        name: String,
    ): BookGroupDetailsDto = details(kind, repository.create(kind, normalizeName(name)))

    fun rename(
        kind: BookGroupKind,
        id: Long,
        name: String,
    ): BookGroupDetailsDto {
        requireGroup(kind, id, true)
        repository.rename(kind, id, normalizeName(name))
        return details(kind, id)
    }

    fun delete(
        kind: BookGroupKind,
        id: Long,
    ) {
        requireGroup(kind, id, true)
        repository.delete(kind, id)
    }

    fun add(
        kind: BookGroupKind,
        id: Long,
        childId: Long,
    ): BookGroupDetailsDto {
        requireGroup(kind, id, true)
        if (!repository.childExists(kind, childId)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Item não encontrado")
        repository.add(kind, id, childId)
        return details(kind, id)
    }

    fun remove(
        kind: BookGroupKind,
        id: Long,
        childId: Long,
    ): BookGroupDetailsDto {
        requireGroup(kind, id, true)
        repository.remove(kind, id, childId)
        return details(kind, id)
    }

    fun order(
        kind: BookGroupKind,
        id: Long,
        ids: List<Long>,
    ): BookGroupDetailsDto {
        requireGroup(kind, id, true)
        val current = repository.items(kind, id).map { it.id }
        if (ids.size != current.size ||
            ids.toSet() != current.toSet()
        ) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe todos os itens uma única vez")
        }
        ids.forEachIndexed { index, childId -> repository.position(kind, id, childId, index + 1) }
        return details(kind, id)
    }

    fun apply(
        suggestion: BookSeriesSuggestionDto,
        request: ApplyBookSeriesSuggestionRequest,
    ): BookGroupDetailsDto {
        if (request.bookIds.isEmpty() ||
            request.bookIds.distinct().size != request.bookIds.size ||
            !suggestion.books.map { it.id }.containsAll(request.bookIds)
        ) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione livros locais da sugestão")
        }
        val id = request.seriesId ?: repository.create(BookGroupKind.SERIES, normalizeName(request.name))
        requireGroup(BookGroupKind.SERIES, id, true)
        request.bookIds.forEach {
            requireBook(it)
            repository.add(BookGroupKind.SERIES, id, it)
        }
        repository.markSource(id, suggestion.hardcoverId)
        return details(BookGroupKind.SERIES, id)
    }

    private fun normalizeName(name: String): String =
        name.trim().replace("\\s+".toRegex(), " ").also {
            if (it.isBlank() ||
                it.length > 300
            ) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome obrigatório, com até 300 caracteres")
            }
        }

    private fun requireGroup(
        kind: BookGroupKind,
        id: Long,
        lock: Boolean = false,
    ): String = repository.name(kind, id, lock) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Agrupamento não encontrado")

    private fun requireBook(id: Long) {
        if (!repository.bookExists(id)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Livro não encontrado")
    }
}
