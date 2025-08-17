package dev.marcal.mediapulse.server.fixture

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import dev.marcal.mediapulse.server.config.JacksonConfig
import dev.marcal.mediapulse.server.controller.webhook.dto.PlexWebhookPayload

object PlexEventsFixture {
    private val objectMapper = JacksonConfig().objectMapper()

    private fun readMusicEventsResource(): String =
        requireNotNull(this::class.java.getResourceAsStream("/mocks/plex/music/media-music-events.json"))
            .bufferedReader()
            .use { it.readText() }

    private fun parseJsonNodes(json: String): List<JsonNode> = objectMapper.readValue(json, object : TypeReference<List<JsonNode>>() {})

    private fun parsePayloads(nodes: List<JsonNode>): List<Triple<PlexWebhookPayload, String, ObjectNode>> =
        nodes.map { node ->
            val json = node.toString()
            Triple(objectMapper.readValue(json, PlexWebhookPayload::class.java), json, (node as ObjectNode))
        }

    private val musicEventsPair: List<Triple<PlexWebhookPayload, String, ObjectNode>> by lazy {
        val json = readMusicEventsResource()
        val nodes = parseJsonNodes(json)
        parsePayloads(nodes)
    }

    val musicEventsJsonNode: List<ObjectNode> by lazy { musicEventsPair.map { it.third } }
    val musicEventsJson: List<String> by lazy { musicEventsPair.map { it.second } }
    val musicEvents: List<PlexWebhookPayload> by lazy { musicEventsPair.map { it.first } }
}
