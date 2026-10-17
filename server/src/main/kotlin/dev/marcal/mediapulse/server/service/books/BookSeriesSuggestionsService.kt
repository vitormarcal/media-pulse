package dev.marcal.mediapulse.server.service.books

import dev.marcal.mediapulse.server.api.books.BookSeriesSuggestionDto
import dev.marcal.mediapulse.server.config.HardcoverProperties
import dev.marcal.mediapulse.server.integration.hardcover.HardcoverApiClient
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
        if (!properties.enabled ||
            properties.token.isBlank()
        ) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Integração Hardcover indisponível")
        }
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
            BookSeriesSuggestionDto(
                series.id,
                series.name,
                series.books
                    .flatMapIndexed { index, item ->
                        repository.localBooks(
                            item.bookId,
                            index + 1,
                        )
                    }.distinctBy { it.id },
            )
        }
    }
}
