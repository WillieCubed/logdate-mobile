package app.logdate.client.data.journals

import app.logdate.client.database.dao.JournalDao
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.model.Journal
import kotlin.uuid.Uuid

internal class JournalLocalMutations(
    private val journalDao: JournalDao,
    private val metadata: SyncMetadataService,
    private val transactions: SyncTransactionManager?,
    private val currentScope: suspend () -> JournalMergeScope,
    private val resolve: suspend (Uuid) -> Uuid,
    private val changed: () -> Unit,
    private val receipts: ArchiveRecoveryReceipts,
) {
    suspend fun recoveryScope(): JournalMergeScope? = currentScope().takeIf { it.ownerId.isNotBlank() && it.serverOrigin.isNotBlank() }

    private suspend fun <T> transaction(block: suspend () -> T): T =
        if (transactions ==
            null
        ) {
            block()
        } else {
            transactions.withTransaction(block)
        }

    suspend fun create(journal: Journal): Uuid {
        transaction {
            check(resolve(journal.id) == journal.id) { "Journal was merged" }
            journalDao.create(journal.toEntity())
            metadata.enqueuePending(journal.id.toString(), EntityType.JOURNAL, PendingOperation.CREATE)
        }
        changed()
        return journal.id
    }

    suspend fun update(journal: Journal) {
        transaction {
            if (resolve(journal.id) != journal.id) return@transaction
            journalDao.update(journal.toEntity())
            metadata.enqueuePending(journal.id.toString(), EntityType.JOURNAL, PendingOperation.UPDATE)
        }
        changed()
    }

    suspend fun delete(id: Uuid) {
        transaction {
            if (resolve(id) != id) return@transaction
            journalDao.delete(id)
            metadata.enqueuePending(id.toString(), EntityType.JOURNAL, PendingOperation.DELETE)
        }
        changed()
    }

    suspend fun restore(
        expected: Journal?,
        replacement: Journal,
        scope: JournalMergeScope?,
        recoveryId: String?,
    ): Boolean {
        val saved =
            transaction {
                check(recoveryScope() == scope) { "Journal recovery account changed" }
                if (scope != null &&
                    recoveryId != null &&
                    receipts.contains(scope, "JOURNAL", recoveryId, replacement.id.toString())
                ) {
                    return@transaction false
                }
                if (resolve(replacement.id) != replacement.id ||
                    journalDao.getJournalById(replacement.id)?.toModel() != expected
                ) {
                    if (scope != null && recoveryId != null) receipts.record(scope, "JOURNAL", recoveryId, replacement.id.toString())
                    check(recoveryScope() == scope) { "Journal recovery account changed" }
                    return@transaction false
                }
                if (expected == replacement) {
                    if (scope != null && recoveryId != null) receipts.record(scope, "JOURNAL", recoveryId, replacement.id.toString())
                    check(recoveryScope() == scope) { "Journal recovery account changed" }
                    return@transaction true
                }
                val operation = if (expected == null) PendingOperation.CREATE else PendingOperation.UPDATE
                val uploadScope = scope?.let { UploadScope(it.ownerId, it.serverOrigin) }
                if (!metadata.enqueueRestoreMutation(
                        replacement.id.toString(),
                        EntityType.JOURNAL,
                        operation,
                        uploadScope,
                    )
                ) {
                    if (scope != null && recoveryId != null) receipts.record(scope, "JOURNAL", recoveryId, replacement.id.toString())
                    check(recoveryScope() == scope) { "Recovery account changed" }
                    return@transaction false
                }
                if (expected == null) journalDao.create(replacement.toEntity()) else journalDao.update(replacement.toEntity())
                if (scope != null && recoveryId != null) receipts.record(scope, "JOURNAL", recoveryId, replacement.id.toString())
                check(recoveryScope() == scope) { "Journal recovery account changed" }
                true
            }
        if (saved) changed()
        return saved
    }
}
