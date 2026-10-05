package dev.marcal.mediapulse.server.api.magazine

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer

// Avoid Jackson silently truncating fractional page counts.
class MagazineIntegerDeserializer : JsonDeserializer<Int>() {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext,
    ): Int {
        if (parser.currentToken != JsonToken.VALUE_NUMBER_INT) {
            return context.handleUnexpectedToken(Int::class.java, parser) as Int
        }
        return parser.intValue
    }
}
