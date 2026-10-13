package dev.marcal.mediapulse.server.service.image

import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.repository.crud.BookRepository
import dev.marcal.mediapulse.server.repository.crud.GameImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.GameRepository
import dev.marcal.mediapulse.server.util.TxUtil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class LibraryCoverService(
    private val books: BookRepository,
    private val games: GameRepository,
    private val images: GameImageCrudRepository,
    private val storage: LibraryImageStorageService,
    private val tx: TxUtil,
) {
    fun book(
        id: Long,
        cover: MultipartFile,
    ): String {
        val book = books.findById(id).orElseThrow { missing() }
        val url = storage.saveBook(content(cover), id, book.title, "MANUAL", fileHint = UUID.randomUUID().toString())
        tx.inTx {
            if (books.updateCover(id, url) != 1) missing()
        }
        return url
    }

    fun game(
        id: Long,
        cover: MultipartFile,
    ): String {
        val game = games.findById(id).orElseThrow { missing() }
        val url = storage.saveGame(content(cover), id, game.title, "MANUAL", UUID.randomUUID().toString())
        tx.inTx {
            if (images.lockGameRowForPrimaryUpdate(id) == null) missing()
            images.insertIgnore(id, url, "COVER", false)
            images.clearPrimaryForGame(id)
            images.markPrimaryForGame(id, url)
            if (games.updateCover(id, url) != 1) missing()
        }
        return url
    }

    private fun content(file: MultipartFile): ImageContent {
        if (file.isEmpty || file.size > LibraryImageStorageService.MAX_BYTES) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha uma imagem de até 10 MB.")
        }
        return ImageContent(file.bytes, null)
    }

    private fun missing(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado.")
}
