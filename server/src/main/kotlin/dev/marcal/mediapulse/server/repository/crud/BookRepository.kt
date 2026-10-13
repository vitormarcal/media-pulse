package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.book.Book
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface BookRepository : JpaRepository<Book, Long> {
    @Modifying
    @Query(nativeQuery = true, value = "UPDATE books SET cover_url = :url, updated_at = NOW() WHERE id = :id")
    fun updateCover(
        id: Long,
        url: String,
    ): Int
}
