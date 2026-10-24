package app.logdate.client.data.journals

import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.dao.JournalDao
import app.logdate.client.database.entities.journals.JournalMergeEntity
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.shared.model.Journal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** Applies authoritative journal sync mutations without involving retained-source replay. */
internal class JournalSyncMutations(
    private val journalDao: JournalDao,
    private val dispatcher: CoroutineDispatcher,
    private val database: LogDateDatabase?,
    private val transactions: SyncTransactionManager?,
    private val receipts: ArchiveRecoveryReceipts,
    private val currentScope: suspend () -> JournalMergeScope,
    private val resolve: suspend (JournalMergeScope, Uuid) -> Uuid,
) {
    private val outbox get() = checkNotNull(database).syncMetadataDao()

    private suspend fun <T> transaction(block: suspend () -> T): T =
        if (database == null) {
            block()
        } else {
            checkNotNull(transactions).withTransaction(block)
        }

    suspend fun createFromSync(journal: Journal) =
        withContext(dispatcher) {
            transaction {
                val scope = currentScope()
                if (resolve(scope, journal.id) == journal.id) {
                    journalDao.update(journal.toEntity())
                    receipts.clearLocalDeletion(scope, LocalDeletionType.JOURNAL, journal.id.toString())
                }
                check(currentScope() == scope) { "Journal sync account changed" }
            }
        }

    suspend fun updateFromSync(journal: Journal) =
        withContext(dispatcher) {
            transaction {
                val scope = currentScope()
                if (resolve(scope, journal.id) == journal.id) journalDao.update(journal.toEntity())
                check(currentScope() == scope) { "Journal sync account changed" }
            }
        }

    suspend fun deleteFromSync(journalId: Uuid) =
        withContext(dispatcher) {
            transaction {
                if (database == null) {
                    journalDao.delete(journalId)
                } else {
                    val scope = currentScope()
                    receipts.recordLocalDeletion(scope, LocalDeletionType.JOURNAL, journalId.toString())
                    val resolvedId = resolve(scope, journalId)
                    if (resolvedId != journalId) {
                        receipts.recordLocalDeletion(scope, LocalDeletionType.JOURNAL, resolvedId.toString())
                    }
                    checkNotNull(database)
                        .journalMergeDao()
                        .all(scope.ownerId, scope.serverOrigin)
                        .filter { it.pending && resolve(scope, it.destinationId) == journalId }
                        .forEach { row ->
                            val retained = recoveryContents(scope, row)
                            database.journalMergeDao().put(
                                row.copy(recoveryContentIds = Json.encodeToString(retained.map { it.toString() })),
                            )
                        }
                    journalDao.delete(journalId)
                    check(currentScope() == scope) { "Journal deletion account changed" }
                }
            }
        }

    suspend fun recoveryContents(
        scope: JournalMergeScope,
        row: JournalMergeEntity,
    ): Set<Uuid> {
        val target = resolve(scope, row.destinationId)
        val pending =
            outbox
                .getPendingByType(scope.ownerId, scope.serverOrigin, "ASSOCIATION")
                .filter { it.operation == "CREATE" }
                .mapNotNull { AssociationPendingKey.fromPendingId(it.entityId) }
                .filter { it.journalId == target }
                .map { it.contentId }
        return row.toOperation().contentIds + Json.decodeFromString<List<String>>(row.recoveryContentIds).map(Uuid::parse) + pending
    }

    suspend fun suppressSourceOutbox(
        scope: JournalMergeScope,
        sourceId: Uuid,
    ) {
        outbox.deletePending(scope.ownerId, scope.serverOrigin, "JOURNAL", sourceId.toString())
        outbox
            .getPendingByType(scope.ownerId, scope.serverOrigin, "ASSOCIATION")
            .filter { AssociationPendingKey.fromPendingId(it.entityId)?.journalId == sourceId }
            .forEach { outbox.deletePending(scope.ownerId, scope.serverOrigin, "ASSOCIATION", it.entityId) }
    }
}
