package dev.marcal.mediapulse.server.util

import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Component
class TxUtil(
    txManager: PlatformTransactionManager,
) {
    private val template = TransactionTemplate(txManager)

    fun <T> inTx(block: () -> T): T = template.execute { block() }!!
}
