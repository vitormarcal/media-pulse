package dev.marcal.mediapulse.server.model.image

import org.springframework.http.MediaType

data class ImageContent(
    val bytes: ByteArray,
    val contentType: MediaType?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImageContent

        if (!bytes.contentEquals(other.bytes)) return false
        if (contentType != other.contentType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + (contentType?.hashCode() ?: 0)
        return result
    }
}
