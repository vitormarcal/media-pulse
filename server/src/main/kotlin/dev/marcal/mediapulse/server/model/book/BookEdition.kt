package dev.marcal.mediapulse.server.model.book

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "book_editions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["fingerprint"])],
)
data class BookEdition(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "book_id", nullable = false)
    val bookId: Long,
    val title: String? = null,
    @Column(name = "isbn_10")
    val isbn10: String? = null,
    @Column(name = "isbn_13")
    val isbn13: String? = null,
    val pages: Int? = null,
    val language: String? = null,
    val publisher: String? = null,
    val format: String? = null,
    @Column(name = "edition_information", columnDefinition = "TEXT")
    val editionInformation: String? = null,
    @Column(name = "cover_url")
    val coverUrl: String? = null,
    @Column(nullable = false, unique = true)
    val fingerprint: String,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at")
    val updatedAt: Instant? = null,
)
