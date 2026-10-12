package dev.marcal.mediapulse.server.api.artwork

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode

class ArtworkMovementsDeserializer : JsonDeserializer<List<String>>() {
    override fun deserialize(
        parser: JsonParser,
        context: DeserializationContext,
    ): List<String> {
        val node = parser.codec.readTree<JsonNode>(parser)
        if (!node.isArray || node.any { !it.isTextual }) {
            throw JsonMappingException.from(parser, "Movimentos devem ser uma lista de textos.")
        }
        return node.map { it.asText() }
    }
}
