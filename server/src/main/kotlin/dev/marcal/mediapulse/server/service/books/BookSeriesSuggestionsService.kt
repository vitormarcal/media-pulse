package dev.marcal.mediapulse.server.service.books

import dev.marcal.mediapulse.server.api.books.BookSeriesMetadataDto
import dev.marcal.mediapulse.server.api.books.BookSeriesProviderAuthorDto
import dev.marcal.mediapulse.server.api.books.BookSeriesSuggestionDto
import dev.marcal.mediapulse.server.config.HardcoverProperties
import dev.marcal.mediapulse.server.integration.hardcover.HardcoverApiClient
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverSuggestedSeries
import dev.marcal.mediapulse.server.model.book.BookGroupKind
import dev.marcal.mediapulse.server.repository.BookSeriesRepository
import kotlinx.coroutines.CancellationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class BookSeriesSuggestionsService(
    private val repository: BookSeriesRepository,
    private val client: HardcoverApiClient,
    private val properties: HardcoverProperties,
) {
    suspend fun preview(bookId: Long): List<BookSeriesSuggestionDto> {
        if (!repository.bookExists(bookId)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Livro não encontrado")
        val hardcoverId =
            repository.hardcoverId(bookId)
                ?: throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sincronize o Hardcover para vincular este livro antes de buscar sugestões",
                )
        requireProvider()
        val suggestions =
            try {
                client.fetchSeries(hardcoverId)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                throw ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Não foi possível buscar sugestões no Hardcover. Tente novamente.",
                    ex,
                )
            }
        return suggestions.map { series ->
            toSuggestion(series).copy(
                books =
                    series.books
                        .flatMapIndexed { index, item ->
                            repository.localBooks(item.bookId, index + 1)
                        }.distinctBy { it.id },
            )
        }
    }

    suspend fun previewSeries(id: Long): BookSeriesSuggestionDto {
        if (repository.name(BookGroupKind.SERIES, id) ==
            null
        ) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Série não encontrada")
        }
        val hardcoverId =
            repository.seriesHardcoverId(id)
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Esta série não está vinculada ao Hardcover")
        requireProvider()
        val series =
            try {
                client.fetchSeriesById(hardcoverId)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                throw ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Não foi possível buscar os metadados no Hardcover. Tente novamente.",
                    ex,
                )
            } ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Série não encontrada no Hardcover")
        return toSuggestion(series)
    }

    private fun toSuggestion(series: HardcoverSuggestedSeries): BookSeriesSuggestionDto =
        BookSeriesSuggestionDto(
            hardcoverId = series.id,
            name = series.name,
            books = emptyList(),
            description = series.description,
            hardcover =
                BookSeriesMetadataDto(
                    series.slug,
                    series.author?.let {
                        BookSeriesProviderAuthorDto(it.id, it.name)
                    },
                    series.isCompleted,
                    series.booksCount,
                    series.primaryBooksCount,
                ),
        )

    private fun requireProvider() {
        if (!properties.enabled ||
            properties.token.isBlank()
        ) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Integração Hardcover indisponível")
        }
    }
}
