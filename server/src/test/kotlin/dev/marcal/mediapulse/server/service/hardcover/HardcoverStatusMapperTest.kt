package dev.marcal.mediapulse.server.service.hardcover

import dev.marcal.mediapulse.server.model.book.BookReadStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HardcoverStatusMapperTest {
    @Test
    fun `maps known slugs`() {
        assertEquals(BookReadStatus.READ, HardcoverStatusMapper.mapSlug("read"))
        assertEquals(BookReadStatus.CURRENTLY_READING, HardcoverStatusMapper.mapSlug("currently-reading"))
        assertEquals(BookReadStatus.WANT_TO_READ, HardcoverStatusMapper.mapSlug("want-to-read"))
        assertEquals(BookReadStatus.DID_NOT_FINISH, HardcoverStatusMapper.mapSlug("did-not-finish"))
        assertEquals(BookReadStatus.PAUSED, HardcoverStatusMapper.mapSlug("paused"))
    }

    @Test
    fun `unknown slug maps to UNKNOWN`() {
        assertEquals(BookReadStatus.UNKNOWN, HardcoverStatusMapper.mapSlug("something-else"))
        assertEquals(BookReadStatus.UNKNOWN, HardcoverStatusMapper.mapSlug(null))
    }
}
