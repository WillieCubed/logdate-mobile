package app.logdate.client.data.notes

import app.logdate.client.sync.SyncTransactionManager
import kotlin.time.Instant

internal sealed interface PendingMediaIndex {
    val uri: String
    val timestamp: Instant

    data class Image(
        override val uri: String,
        override val timestamp: Instant,
    ) : PendingMediaIndex

    data class Video(
        override val uri: String,
        override val timestamp: Instant,
        val duration: kotlin.time.Duration,
    ) : PendingMediaIndex
}

internal object PassthroughSyncTransactionManager : SyncTransactionManager {
    override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
}
