package dev.marcal.mediapulse.server.model

import dev.marcal.mediapulse.server.controller.webhook.dto.PlexWebhookPayload
import dev.marcal.mediapulse.server.model.music.MusicSourceIdentifier
import dev.marcal.mediapulse.server.model.series.EpisodeSourceIdentifier

object PlexSourceIdentifierParser {
    fun toIdentifiers(meta: PlexWebhookPayload.PlexMetadata): List<Pair<SourceIdentifier, String>> =
        meta.guid.map { guid ->
            val parts = guid.id.split("://")
            require(parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                "Invalid GUID format: ${guid.id}"
            }
            val sourceIdentifier =
                SourceIdentifier.fromTag(parts[0])
                    ?: throw IllegalArgumentException("Unknown source identifier: ${parts[0]}")
            sourceIdentifier to parts[1]
        }

    fun toMusicIdentifiers(meta: PlexWebhookPayload.PlexMetadata): List<MusicSourceIdentifier> =
        toIdentifiers(meta).map { (sourceIdentifier, externalId) ->
            MusicSourceIdentifier(
                externalType = sourceIdentifier,
                externalId = externalId,
            )
        }

    fun toEpisodeIdentifiers(meta: PlexWebhookPayload.PlexMetadata): List<EpisodeSourceIdentifier> =
        toIdentifiers(meta).map { (sourceIdentifier, externalId) ->
            EpisodeSourceIdentifier(
                externalType = sourceIdentifier,
                externalId = externalId,
            )
        }
}
