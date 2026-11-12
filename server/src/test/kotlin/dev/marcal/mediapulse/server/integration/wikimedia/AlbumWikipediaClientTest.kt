package dev.marcal.mediapulse.server.integration.wikimedia

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbUrlRelation
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbUrlResource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlbumWikipediaClientTest {
    private val mapper = jacksonObjectMapper()
    private val requests = mutableListOf<URI>()
    private var fail = false
    private var missingPortuguese = false
    private var emptyEnglish = false
    private val client =
        WikipediaIntroductionClient(
            WebClient
                .builder()
                .exchangeFunction { request ->
                    requests += request.url()
                    if (fail) return@exchangeFunction Mono.just(ClientResponse.create(HttpStatus.BAD_GATEWAY).build())
                    val json =
                        if (request.url().host == "www.wikidata.org") {
                            mapOf(
                                "entities" to
                                    mapOf(
                                        "Q123" to
                                            mapOf(
                                                "sitelinks" to
                                                    mapOf(
                                                        "ptwiki" to mapOf("title" to "Disco (álbum)"),
                                                        "enwiki" to mapOf("title" to "Record (album)"),
                                                    ),
                                            ),
                                    ),
                            )
                        } else {
                            val language = request.url().host.substringBefore('.')
                            val page =
                                if (language == "pt" && missingPortuguese) {
                                    mapOf("missing" to true)
                                } else {
                                    mapOf(
                                        "ns" to 0,
                                        "title" to "Record",
                                        "extract" to if (emptyEnglish && language == "en") "" else "Texto $language",
                                        "fullurl" to "https://$language.wikipedia.org/wiki/Record",
                                        "revisions" to listOf(mapOf("revid" to 42)),
                                        "langlinks" to
                                            if (language == "en") {
                                                listOf(mapOf("lang" to "pt", "title" to "Disco"))
                                            } else {
                                                emptyList()
                                            },
                                    )
                                }
                            mapOf(
                                "query" to
                                    mapOf(
                                        "pages" to listOf(page),
                                        "rightsinfo" to
                                            mapOf(
                                                "text" to "CC BY-SA 4.0",
                                                "url" to "https://creativecommons.org/licenses/by-sa/4.0/",
                                            ),
                                    ),
                            )
                        }
                    Mono.just(
                        ClientResponse
                            .create(HttpStatus.OK)
                            .header("Content-Type", "application/json")
                            .body(mapper.writeValueAsString(json))
                            .build(),
                    )
                }.build(),
        )

    private fun relation(
        type: String,
        url: String,
    ) = MbUrlRelation(type, MbUrlResource(url))

    @Test
    fun `external links allow known album sources and reject other schemes and hosts`() {
        val links =
            AlbumWikipediaClient(client).externalLinks(
                listOf(
                    relation("streaming", "https://open.spotify.com/album/123"),
                    relation("purchase", "https://artist.bandcamp.com/album/record"),
                    relation("discogs", "https://www.discogs.com/release/123"),
                    relation("streaming", "javascript:alert(1)"),
                    relation("streaming", "https://open.spotify.com.attacker.test/album/123"),
                ),
                "rg",
            )
        assertEquals(setOf("MusicBrainz", "Spotify", "Bandcamp", "Discogs"), links.keys)
    }

    @Test
    fun `wikidata sitelinks prefer Portuguese and retain revision and license`() =
        runBlocking {
            val intro = client.introduction(listOf(relation("wikidata", "https://www.wikidata.org/wiki/Q123")))!!
            assertEquals("pt", intro.language)
            assertEquals("Texto pt", intro.text)
            assertEquals(42L, intro.revisionId)
            assertEquals("CC BY-SA 4.0", intro.license)
            assertEquals(listOf("www.wikidata.org", "pt.wikipedia.org"), requests.map { it.host })
        }

    @Test
    fun `missing Portuguese falls back to English`() =
        runBlocking {
            missingPortuguese = true
            assertEquals("en", client.introduction(listOf(relation("wikidata", "https://www.wikidata.org/entity/Q123")))!!.language)
        }

    @Test
    fun `direct English link discovers Portuguese translation`() =
        runBlocking {
            assertEquals(
                "pt",
                client.introduction(listOf(relation("wikipedia", "https://en.wikipedia.org/wiki/Record_(album)")))!!.language,
            )
            assertEquals(listOf("en.wikipedia.org", "pt.wikipedia.org"), requests.map { it.host })
        }

    @Test
    fun `foreign hosts and credentialed URLs are not fetched`() =
        runBlocking {
            assertNull(
                client.introduction(
                    listOf(
                        relation("wikipedia", "https://en.wikipedia.org.attacker.test/wiki/Record"),
                        relation("wikidata", "https://127.0.0.1/wiki/Q123"),
                        relation("wikipedia", "https://user@en.wikipedia.org/wiki/Record"),
                    ),
                ),
            )
            assertTrue(requests.isEmpty())
        }

    @Test
    fun `empty extracts return absence while upstream failures propagate`() =
        runBlocking {
            missingPortuguese = true
            emptyEnglish = true
            assertNull(client.introduction(listOf(relation("wikipedia", "https://en.wikipedia.org/wiki/Record"))))
            fail = true
            assertFailsWith<Exception> { client.introduction(listOf(relation("wikipedia", "https://pt.wikipedia.org/wiki/Disco"))) }
            Unit
        }
}
