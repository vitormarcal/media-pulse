package dev.marcal.mediapulse.server.integration.hardcover

import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverAuthorProfile
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverAuthorProfileData
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverGraphQLRequest
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverGraphQLResponse
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverSeriesData
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverSeriesLookupData
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverSuggestedSeries
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverUserBook
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverUserBooksData
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class HardcoverApiClient(
    private val hardcoverWebClient: WebClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun fetchAuthorProfile(
        id: Long?,
        slug: String?,
    ): HardcoverAuthorProfile? {
        val filter = if (id != null) "id: {_eq: ${'$'}id}" else "slug: {_eq: ${'$'}slug}"
        val variable = if (id != null) "${'$'}id: Int!" else "${'$'}slug: String!"
        val request =
            HardcoverGraphQLRequest(
                """
                query AuthorProfile($variable) {
                    authors(where: {$filter}, limit: 1) {
                        id name slug bio born_date born_year death_date death_year location links image { url }
                    }
                }
                """.trimIndent(),
                if (id != null) mapOf("id" to id) else mapOf("slug" to slug),
            )
        val response =
            hardcoverWebClient
                .post()
                .bodyValue(request)
                .retrieve()
                .bodyToMono(
                    object : ParameterizedTypeReference<HardcoverGraphQLResponse<HardcoverAuthorProfileData>>() {},
                ).awaitSingle()
        if (!response.errors.isNullOrEmpty() || response.data == null) error("Hardcover author query failed")
        return response.data.authors.firstOrNull()
    }

    suspend fun fetchSeries(bookId: Long): List<HardcoverSuggestedSeries> {
        val request =
            HardcoverGraphQLRequest(
                query =
                    """
                    query SeriesSuggestions(${'$'}bookId: Int!) {
                      book_series(where: {book_id: {_eq: ${'$'}bookId}}) {
                        series {
                          id name description slug is_completed books_count primary_books_count
                          author { id name }
                          book_series(order_by: [{position: asc_nulls_last}, {book_id: asc}]) { book_id position }
                        }
                      }
                    }
                    """.trimIndent(),
                variables = mapOf("bookId" to bookId),
            )
        val response =
            hardcoverWebClient
                .post()
                .bodyValue(request)
                .retrieve()
                .bodyToMono(
                    object : ParameterizedTypeReference<HardcoverGraphQLResponse<HardcoverSeriesData>>() {},
                ).awaitSingle()
        if (!response.errors.isNullOrEmpty() || response.data == null) {
            throw IllegalStateException("Hardcover series query failed")
        }
        return response.data.entries
            .mapNotNull { it.series }
            .distinctBy { it.id }
    }

    suspend fun fetchSeriesById(seriesId: Long): HardcoverSuggestedSeries? {
        val request =
            HardcoverGraphQLRequest(
                query =
                    """
                    query SeriesContext(${'$'}seriesId: Int!) {
                      series_by_pk(id: ${'$'}seriesId) {
                        id name description slug is_completed books_count primary_books_count author { id name }
                      }
                    }
                    """.trimIndent(),
                variables = mapOf("seriesId" to seriesId),
            )
        val response =
            hardcoverWebClient
                .post()
                .bodyValue(request)
                .retrieve()
                .bodyToMono(object : ParameterizedTypeReference<HardcoverGraphQLResponse<HardcoverSeriesLookupData>>() {})
                .awaitSingle()
        if (!response.errors.isNullOrEmpty() || response.data == null) throw IllegalStateException("Hardcover series context query failed")
        return response.data.series
    }

    suspend fun fetchUserBooksPage(
        userId: Long,
        limit: Int,
        offset: Int,
    ): List<HardcoverUserBook> {
        val query =
            """
            query UserBooks(${"$"}userId: Int!, ${"$"}limit: Int!, ${"$"}offset: Int!) {
              user_books(
                where: {user_id: {_eq: ${"$"}userId}},
                order_by: {id: asc},
                limit: ${"$"}limit,
                offset: ${"$"}offset
              ) {
                id
                updated_at
                first_read_date
                first_started_reading_date
                last_read_date
                rating
                has_review
                review_raw
                reviewed_at
                read_count
                user_book_status { slug status }
                user_book_reads { id started_at finished_at progress progress_pages edition_id }
                edition {
                  id
                  title
                  isbn_10
                  isbn_13
                  pages
                  edition_format
                  release_date
                  edition_information
                  language { code2 }
                  publisher { name }
                  image { url }
                  images { url }
                  contributions { contribution author { name } }
                }
                book {
                  id
                  title
                  release_date
                  pages
                  description
                }
              }
            }
            """.trimIndent()

        val request =
            HardcoverGraphQLRequest(
                query = query,
                variables =
                    mapOf(
                        "userId" to userId,
                        "limit" to limit,
                        "offset" to offset,
                    ),
            )

        val response =
            hardcoverWebClient
                .post()
                .bodyValue(request)
                .retrieve()
                .toEntity(object : ParameterizedTypeReference<HardcoverGraphQLResponse<HardcoverUserBooksData>>() {})
                .awaitSingle()
                .body

        if (response?.errors?.isNotEmpty() == true) {
            val msg = response.errors.joinToString(" | ") { it.message ?: "unknown" }
            logger.warn("Hardcover GraphQL errors | {}", msg)
        }

        return response?.data?.userBooks ?: emptyList()
    }
}
