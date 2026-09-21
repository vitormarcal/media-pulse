package dev.marcal.mediapulse.server.api.workout

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer

// Avoid Jackson silently truncating fractional durations or jump counts.
class WorkoutIntegerDeserializer : JsonDeserializer<Int>() {
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
