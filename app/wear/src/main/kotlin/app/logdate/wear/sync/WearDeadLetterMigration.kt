package app.logdate.wear.sync

import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncDeadLetterRecord
import app.logdate.client.sync.metadata.SyncDeadLetterStore
import app.logdate.client.sync.metadata.SyncMetadataService
import io.github.aakira.napier.Napier

/**
 * Puts captures that older versions dropped from the outbox back in it.
 *
 * Those versions gave up on an item after five failed attempts by marking it synced, which took it out
 * of the outbox and so out of backup, and recorded it under an id of the form `NOTE_<id>`. Each such
 * record is queued again with a fresh attempt budget and then removed. Records this version writes,
 * named by an operation id or `NOTE:<id>`, are left alone, so running this at every launch is safe. A
 * record whose kind this version does not know stays, and with it the sync issue it shows.
 */
class WearDeadLetterMigration(
    private val deadLetterStore: SyncDeadLetterStore,
    private val syncMetadataService: SyncMetadataService,
) {
    /** @return how many items were queued again. */
    suspend fun migrate(): Int =
        deadLetterStore
            .list()
            .filter { it.id == "${it.entityType}_${it.entityId}" }
            .count { record -> requeue(record) }

    private suspend fun requeue(record: SyncDeadLetterRecord): Boolean {
        val entityType = runCatching { EntityType.valueOf(record.entityType) }.getOrNull()
        val operation = runCatching { PendingOperation.valueOf(record.operation) }.getOrNull()
        if (entityType == null || operation == null) {
            Napier.e("Cannot queue ${record.id} again: unknown type ${record.entityType} or operation ${record.operation}")
            return false
        }
        syncMetadataService.enqueuePending(record.entityId, entityType, operation)
        deadLetterStore.remove(record.id)
        Napier.i("Queued ${record.id} again after an older version set it aside")
        return true
    }
}
