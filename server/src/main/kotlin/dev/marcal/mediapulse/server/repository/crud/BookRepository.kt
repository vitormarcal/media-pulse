package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.book.Book
import org.springframework.data.jpa.repository.JpaRepository

interface BookRepository : JpaRepository<Book, Long>
