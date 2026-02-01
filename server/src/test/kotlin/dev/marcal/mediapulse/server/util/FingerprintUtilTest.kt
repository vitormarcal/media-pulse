package dev.marcal.mediapulse.server.util

import org.apache.commons.codec.digest.DigestUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FingerprintUtilTest {
    @Test
    fun `normalize should lowercase, trim, and strip punctuation`() {
        val input = "  The   Name! (Test) "
        val normalized = FingerprintUtil.normalize(input)
        assertEquals("the name (test)", normalized)
    }

    @Test
    fun `authorFp should hash normalized name`() {
        val name = "Jane Doe"
        val expected = DigestUtils.sha256Hex(FingerprintUtil.normalize(name))
        assertEquals(expected, FingerprintUtil.authorFp(name))
    }

    @Test
    fun `bookFp should hash normalized title`() {
        val title = "  The Book Title! "
        val expected = DigestUtils.sha256Hex(FingerprintUtil.normalize(title))
        assertEquals(expected, FingerprintUtil.bookFp(title))
    }

    @Test
    fun `editionFp should prefer isbn13 when present`() {
        val isbn13 = "9781234567890"
        val expected = DigestUtils.sha256Hex("isbn13:$isbn13")
        assertEquals(expected, FingerprintUtil.editionFp(isbn13, 42))
    }

    @Test
    fun `editionFp should fallback to hardcover edition id`() {
        val expected = DigestUtils.sha256Hex("hardcover_edition_id:42")
        assertEquals(expected, FingerprintUtil.editionFp(null, 42))
    }
}
