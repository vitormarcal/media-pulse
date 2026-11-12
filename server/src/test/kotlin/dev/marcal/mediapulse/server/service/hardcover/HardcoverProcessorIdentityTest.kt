package dev.marcal.mediapulse.server.service.hardcover

import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverAuthor
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverBook
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverContribution
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverEdition
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverUserBook
import dev.marcal.mediapulse.server.repository.hardcover.HardcoverAuthorIdentityRepository
import dev.marcal.mediapulse.server.repository.hardcover.HardcoverNativeRepository
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class HardcoverProcessorIdentityTest {
    private val native = mockk<HardcoverNativeRepository>(relaxed = true)
    private val tx = mockk<TxUtil>()
    private val artwork = mockk<HardcoverArtworkService>(relaxed = true)
    private val identities = mockk<HardcoverAuthorIdentityRepository>()
    private val processor = HardcoverUserBookProcessor(native, tx, artwork, identities)

    init {
        every { tx.inTx<Any?>(any()) } answers { firstArg<() -> Any?>().invoke() }
    }

    @Test fun `same-name contributors retain different ids and individual roles`() =
        runBlocking {
            every { identities.resolve("Homônimo", 42) } returns 10
            every { identities.resolve("Homônimo", 43) } returns 11
            processor.process(
                HardcoverUserBook(
                    1,
                    book = HardcoverBook(id = 5, title = "Livro"),
                    edition =
                        HardcoverEdition(
                            contributions =
                                listOf(
                                    HardcoverContribution(null, HardcoverAuthor("Homônimo", 42)),
                                    HardcoverContribution("Translator", HardcoverAuthor("Homônimo", 43)),
                                ),
                        ),
                ),
            )
            verify { native.linkBookAuthor(any(), 10, "AUTHOR") }
            verify { native.linkBookAuthor(any(), 11, "TRANSLATOR") }
            verify(exactly = 0) { native.ensureAuthorId(any(), any()) }
        }

    @Test fun `legacy contributions without external id continue through name resolver`() =
        runBlocking {
            every { native.ensureAuthorId("Legado", any()) } returns 15
            processor.process(
                HardcoverUserBook(
                    1,
                    book = HardcoverBook(id = 5, title = "Livro"),
                    edition =
                        HardcoverEdition(
                            contributions =
                                listOf(
                                    HardcoverContribution(null, HardcoverAuthor("Legado")),
                                ),
                        ),
                ),
            )
            verify { native.linkBookAuthor(any(), 15, "AUTHOR") }
            verify(exactly = 0) { identities.resolve(any(), any()) }
        }
}
