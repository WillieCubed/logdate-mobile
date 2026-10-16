package app.logdate.client.data.journals

import app.logdate.client.database.dao.sync.DownloadInboxDao
import app.logdate.client.database.entities.sync.DownloadInboxEntity
import app.logdate.client.repository.journals.JournalMergeScope

/** Per-entry application receipts join Room writes and survive settling a later deletion. */
interface ArchiveRecoveryReceipts {
    suspend fun contains(
        scope: JournalMergeScope,
        type: String,
        recoveryId: String,
        entityId: String,
    ): Boolean

    suspend fun record(
        scope: JournalMergeScope,
        type: String,
        recoveryId: String,
        entityId: String,
    )

    object None : ArchiveRecoveryReceipts {
        override suspend fun contains(
            scope: JournalMergeScope,
            type: String,
            recoveryId: String,
            entityId: String,
        ) = false

        override suspend fun record(
            scope: JournalMergeScope,
            type: String,
            recoveryId: String,
            entityId: String,
        ) = Unit
    }
}

class RoomArchiveRecoveryReceipts(
    private val inbox: DownloadInboxDao,
) : ArchiveRecoveryReceipts {
    override suspend fun contains(
        scope: JournalMergeScope,
        type: String,
        recoveryId: String,
        entityId: String,
    ): Boolean = inbox.get(scope.ownerId, scope.serverOrigin, "ARCHIVE_$type", "$recoveryId:$entityId") != null

    override suspend fun record(
        scope: JournalMergeScope,
        type: String,
        recoveryId: String,
        entityId: String,
    ) {
        inbox.put(
            DownloadInboxEntity(
                scope.ownerId,
                scope.serverOrigin,
                "ARCHIVE_$type",
                "$recoveryId:$entityId",
                0,
                false,
                "",
                recoveryId,
                "APPLIED",
                0,
                0,
                "NONE",
            ),
        )
    }
}

internal object DirectArchiveRecoveryTransactions : app.logdate.client.sync.SyncTransactionManager {
    override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
}
