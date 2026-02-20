package dev.marcal.mediapulse.server.integration.hardcover.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class HardcoverGraphQLRequest(
    val query: String,
    val variables: Map<String, Any?>,
)

data class HardcoverGraphQLResponse<T>(
    val data: T? = null,
    val errors: List<HardcoverGraphQLError>? = null,
)

data class HardcoverGraphQLError(
    val message: String? = null,
)

data class HardcoverUserBooksData(
    @JsonProperty("user_books")
    val userBooks: List<HardcoverUserBook> = emptyList(),
)

data class HardcoverUserBook(
    val id: Long,
    @JsonProperty("updated_at")
    val updatedAt: String? = null,
    @JsonProperty("first_read_date")
    val firstReadDate: String? = null,
    @JsonProperty("first_started_reading_date")
    val firstStartedReadingDate: String? = null,
    @JsonProperty("last_read_date")
    val lastReadDate: String? = null,
    val rating: BigDecimal? = null,
    @JsonProperty("has_review")
    val hasReview: Boolean? = null,
    @JsonProperty("review_raw")
    val reviewRaw: String? = null,
    @JsonProperty("reviewed_at")
    val reviewedAt: String? = null,
    @JsonProperty("read_count")
    val readCount: Int? = null,
    @JsonProperty("user_book_status")
    val userBookStatus: HardcoverUserBookStatus? = null,
    @JsonProperty("user_book_reads")
    val userBookReads: List<HardcoverUserBookRead>? = null,
    val edition: HardcoverEdition? = null,
    val book: HardcoverBook? = null,
)

data class HardcoverUserBookStatus(
    val slug: String? = null,
    val status: String? = null,
)

data class HardcoverUserBookRead(
    val id: Long? = null,
    @JsonProperty("started_at")
    val startedAt: String? = null,
    @JsonProperty("finished_at")
    val finishedAt: String? = null,
    val progress: Double? = null,
    @JsonProperty("progress_pages")
    val progressPages: Int? = null,
    @JsonProperty("edition_id")
    val editionId: Long? = null,
)

data class HardcoverEdition(
    val id: Long? = null,
    val title: String? = null,
    @JsonProperty("isbn_10")
    val isbn10: String? = null,
    @JsonProperty("isbn_13")
    val isbn13: String? = null,
    val pages: Int? = null,
    @JsonProperty("edition_format")
    val editionFormat: String? = null,
    @JsonProperty("release_date")
    val releaseDate: String? = null,
    @JsonProperty("edition_information")
    val editionInformation: String? = null,
    val language: HardcoverLanguage? = null,
    val publisher: HardcoverPublisher? = null,
    val image: HardcoverImage? = null,
    val contributions: List<HardcoverContribution>? = null,
)

data class HardcoverBook(
    val id: Long? = null,
    val title: String? = null,
    @JsonProperty("release_date")
    val releaseDate: String? = null,
    val pages: Int? = null,
    val description: String? = null,
)

data class HardcoverLanguage(
    @JsonProperty("code2")
    val code2: String? = null,
)

data class HardcoverPublisher(
    val name: String? = null,
)

data class HardcoverImage(
    val url: String? = null,
)

data class HardcoverContribution(
    val contribution: String? = null,
    val author: HardcoverAuthor? = null,
)

data class HardcoverAuthor(
    val name: String? = null,
)
