import dev.marcal.mediapulse.server.controller.dto.WebhookDTO
import dev.marcal.mediapulse.server.repository.WebhookPayloadRepository
import dev.marcal.mediapulse.server.service.WebhookPayloadService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class WebhookPayloadServiceTest {
    private val repository: WebhookPayloadRepository = mock()
    private val service = WebhookPayloadService(repository)

    @Test
    fun `should save payload with correct provider and payload`() {
        val provider = "ifood"
        val payload = """{"key":"value"}"""

        service.save(WebhookDTO(provider = provider, payload = payload))

        verify(repository).save(any())
    }
}
