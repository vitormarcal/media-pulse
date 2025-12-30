package dev.marcal.mediapulse.server.service.spotify

import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.controller.spotify.dto.SpotifyExtendedFileEventPayload
import dev.marcal.mediapulse.server.service.eventsource.EventSourceService
import dev.marcal.mediapulse.server.service.eventsource.ProcessEventSourceService
import org.apache.commons.codec.binary.Hex
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPOutputStream

@Service
class SpotifyExtendedImportService(
    @param:Value("\${media-pulse.storage.imports-path:/data/imports}")
    private val importsPath: String,
    private val eventSourceService: EventSourceService,
    private val processEventSourceService: ProcessEventSourceService,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)

    companion object {
        const val PROVIDER = "spotify_extended_file"
    }

    suspend fun importExtendedHistory(file: MultipartFile): Int {
        if (!running.compareAndSet(false, true)) {
            logger.info("Spotify extended import already running | ignored")
            return -1
        }

        val start = System.currentTimeMillis()
        val originalName = file.originalFilename
        logger.info("Spotify extended import started | originalName={}", originalName)

        try {
            val baseDir = File(importsPath, "spotify/extended")
            baseDir.mkdirs()

            val digest = MessageDigest.getInstance("SHA-256")

            // We compute sha256 of the ORIGINAL bytes while storing a gzip file.
            // This makes dedup stable regardless of compression.
            val tmpFile = File(baseDir, "upload.tmp.gz")
            FileOutputStream(tmpFile).use { fos ->
                GZIPOutputStream(fos).use { gz ->
                    file.inputStream.use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            digest.update(buffer, 0, n)
                            gz.write(buffer, 0, n)
                        }
                        gz.finish()
                    }
                }
            }

            val sha256 = Hex.encodeHexString(digest.digest())
            val finalFile = File(baseDir, "$sha256.json.gz")

            if (!finalFile.exists()) {
                tmpFile.renameTo(finalFile)
            } else {
                tmpFile.delete()
            }

            val payloadObj =
                SpotifyExtendedFileEventPayload(
                    path = finalFile.absolutePath,
                    sha256 = sha256,
                    originalName = originalName,
                    compressed = true,
                    parserVersion = 1,
                )

            val payloadJson = objectMapper.writeValueAsString(payloadObj)

            val saved =
                eventSourceService.save(
                    provider = PROVIDER,
                    payload = payloadJson,
                    fingerprint = sha256,
                )

            logger.info(
                "Spotify extended import queued | eventId={} sha256={} file={}",
                saved.id,
                sha256,
                finalFile.absolutePath,
            )

            processEventSourceService.executeAsync(saved.id)

            return 0
        } finally {
            logger.info("Spotify extended import finished | elapsedMs={}", System.currentTimeMillis() - start)
            running.set(false)
        }
    }
}
