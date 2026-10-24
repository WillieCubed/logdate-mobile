package app.logdate.client.data.journals

import app.logdate.client.database.dao.sync.DownloadInboxDao
import app.logdate.client.database.entities.sync.DownloadInboxEntity
import app.logdate.client.repository.journals.JournalMergeScope

/** Local deletions that an automatic retained-database replay must not reverse. */
enum class LocalDeletionType(
    val inboxEntityType: String,
) {
    JOURNAL("LOCAL_DELETE_JOURNAL"),
    NOTE("LOCAL_DELETE_NOTE"),
    ASSOCIATION("LOCAL_DELETE_ASSOCIATION"),
    TRANSCRIPTION("LOCAL_DELETE_TRANSCRIPTION"),
}

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

    suspend fun containsLocalDeletion(
        scope: JournalMergeScope,
        type: LocalDeletionType,
        entityId: String,
    ): Boolean = false

    suspend fun recordLocalDeletion(
        scope: JournalMergeScope,
        type: LocalDeletionType,
        entityId: String,
    ) = Unit

    suspend fun clearLocalDeletion(
        scope: JournalMergeScope,
        type: LocalDeletionType,
        entityId: String,
    ) = Unit

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

    override suspend fun containsLocalDeletion(
        scope: JournalMergeScope,
        type: LocalDeletionType,
        entityId: String,
    ): Boolean = inbox.get(scope.ownerId, scope.serverOrigin, type.inboxEntityType, entityId)?.state == "APPLIED"

    override suspend fun recordLocalDeletion(
        scope: JournalMergeScope,
        type: LocalDeletionType,
        entityId: String,
    ) {
        inbox.put(
            DownloadInboxEntity(
                scope.ownerId,
                scope.serverOrigin,
                type.inboxEntityType,
                entityId,
                0,
                true,
                "",
                "local-delete-v1",
                "APPLIED",
                0,
                0,
                "NONE",
            ),
        )
    }

    override suspend fun clearLocalDeletion(
        scope: JournalMergeScope,
        type: LocalDeletionType,
        entityId: String,
    ) {
        inbox.deleteReceipt(scope.ownerId, scope.serverOrigin, type.inboxEntityType, entityId)
    }
}

internal object DirectArchiveRecoveryTransactions : app.logdate.client.sync.SyncTransactionManager {
    override suspend fun <T> withTransaction(block: suspend () -> T): T = block()
}
