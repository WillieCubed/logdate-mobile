package app.logdate.client.data.journals

import app.logdate.client.database.LogDateDatabase
import app.logdate.client.database.dao.JournalDao
import app.logdate.client.database.entities.journals.JournalContentEntityLink
import app.logdate.client.database.entities.journals.JournalMergeEntity
import app.logdate.client.database.entities.sync.PendingUploadEntity
import app.logdate.client.repository.journals.DraftRepository
import app.logdate.client.repository.journals.JournalMergeOperation
import app.logdate.client.repository.journals.JournalMergePreview
import app.logdate.client.repository.journals.JournalMergeResult
import app.logdate.client.repository.journals.JournalMergeScope
import app.logdate.client.repository.journals.JournalRepository
import app.logdate.client.repository.journals.SyncableDraftRepository
import app.logdate.client.repository.journals.SyncableJournalRepository
import app.logdate.client.sync.NoOpSyncManager
import app.logdate.client.sync.SyncDebouncer
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.AssociationPendingKey
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.util.platformIODispatcher
import app.logdate.shared.model.EditorDraft
import app.logdate.shared.model.Journal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class OfflineFirstJournalRepository(
    private val journalDao: JournalDao,
    private val remoteDataSource: RemoteJournalDataSource,
    private val draftRepository: DraftRepository,
    private val syncManagerProvider: () -> SyncManager = { NoOpSyncManager },
    private val syncMetadataService: SyncMetadataService,
    private val dispatcher: CoroutineDispatcher = platformIODispatcher,
    private val externalScope: CoroutineScope = CoroutineScope(dispatcher),
    private val database: LogDateDatabase? = null,
    private val currentScope: suspend () -> JournalMergeScope = { JournalMergeScope("", "") },
    private val mergeTransactionManager: SyncTransactionManager? = null,
    private val mergeScopeChanges: Flow<*> = flowOf(Unit),
) : JournalRepository,
    SyncableDraftRepository,
    SyncableJournalRepository {
    private val merges get() = requireNotNull(database).journalMergeDao()
    private val links get() = requireNotNull(database).journalContentDao()
    private val outbox get() = requireNotNull(database).syncMetadataDao()
    private val transactionManager get() = requireNotNull(mergeTransactionManager)
    private val mergeChanges: Flow<Unit>
        get() = if (database == null) flowOf(Unit) else combine(mergeScopeChanges, merges.observeAll()) { _, _ -> Unit }

    private val syncDebouncer =
        SyncDebouncer(scope = externalScope) {
            syncManagerProvider().syncJournals()
        }
    private val draftSyncDebouncer =
        SyncDebouncer(scope = externalScope) {
            syncManagerProvider().syncDrafts()
        }

    override val allJournalsObserved: Flow<List<Journal>>
        get() =
            combine(journalDao.observeAll(), mergeChanges) { journals, _ ->
                journals.filter { resolveJournalId(it.id) == it.id }.map { it.toModel() }
            }

    override fun observeJournalById(id: Uuid): Flow<Journal> =
        flow {
            emitAll(journalDao.observeJournalById(resolveJournalId(id)).map { it.toModel() })
        }

    override suspend fun getJournalById(id: Uuid): Journal? =
        withContext(dispatcher) {
            journalDao.getJournalById(resolveJournalId(id))?.toModel()
        }

    override suspend fun create(journal: Journal): Uuid =
        withContext(dispatcher) {
            withJournalTransaction {
                check(resolveJournalId(journal.id) == journal.id) { "Journal was merged" }
                journalDao.create(journal.toEntity())
                syncMetadataService.enqueuePending(journal.id.toString(), EntityType.JOURNAL, PendingOperation.CREATE)
            }
            syncDebouncer.trigger()
            journal.id
        }

    override suspend fun update(journal: Journal): Unit =
        withContext(dispatcher) {
            withJournalTransaction {
                if (resolveJournalId(journal.id) != journal.id) return@withJournalTransaction
                journalDao.update(journal.toEntity())
                syncMetadataService.enqueuePending(journal.id.toString(), EntityType.JOURNAL, PendingOperation.UPDATE)
            }
            syncDebouncer.trigger()
        }

    override suspend fun delete(journalId: Uuid): Unit =
        withContext(dispatcher) {
            withJournalTransaction {
                if (resolveJournalId(journalId) != journalId) return@withJournalTransaction
                journalDao.delete(journalId)
                syncMetadataService.enqueuePending(journalId.toString(), EntityType.JOURNAL, PendingOperation.DELETE)
            }
            syncDebouncer.trigger()
        }

    private suspend fun <T> withJournalTransaction(block: suspend () -> T): T =
        if (database == null) block() else transactionManager.withTransaction(block)

    override suspend fun saveDraft(draft: EditorDraft) =
        withContext(dispatcher) {
            draftRepository.saveDraft(resolveDraft(draft))
            syncMetadataService.enqueuePending(
                entityId = draft.id.toString(),
                entityType = EntityType.DRAFT,
                operation = PendingOperation.UPDATE,
            )
            draftSyncDebouncer.trigger()
        }

    override suspend fun getLatestDraft(): EditorDraft? =
        withContext(dispatcher) { draftRepository.getLatestDraft()?.let { resolveDraft(it) } }

    override suspend fun getAllDrafts(): List<EditorDraft> =
        withContext(dispatcher) { draftRepository.getAllDrafts().map { resolveDraft(it) } }

    override suspend fun getAllDraftsForSync(): List<EditorDraft> =
        withContext(dispatcher) { draftRepository.getAllDraftsForSync().map { resolveDraft(it) } }

    override suspend fun getDraft(id: Uuid): EditorDraft? =
        withContext(dispatcher) { draftRepository.getDraft(id)?.let { resolveDraft(it) } }

    override suspend fun deleteDraft(id: Uuid) =
        withContext(dispatcher) {
            draftRepository.deleteDraft(id)
            syncMetadataService.enqueuePending(
                entityId = id.toString(),
                entityType = EntityType.DRAFT,
                operation = PendingOperation.DELETE,
            )
            draftSyncDebouncer.trigger()
        }

    override suspend fun saveDraftFromSync(draft: EditorDraft) =
        withContext(dispatcher) {
            draftRepository.saveDraftFromSync(resolveDraft(draft))
        }

    override suspend fun deleteDraftFromSync(id: Uuid) =
        withContext(dispatcher) {
            draftRepository.deleteDraft(id)
        }

    override suspend fun createFromSync(journal: Journal) =
        withContext(dispatcher) {
            withJournalTransaction {
                if (resolveJournalId(journal.id) == journal.id) journalDao.update(journal.toEntity())
            }
        }

    override suspend fun updateFromSync(journal: Journal) =
        withContext(dispatcher) {
            withJournalTransaction {
                if (resolveJournalId(journal.id) == journal.id) journalDao.update(journal.toEntity())
            }
        }

    override suspend fun deleteFromSync(journalId: Uuid) =
        withContext(dispatcher) {
            withJournalTransaction {
                if (database != null) {
                    val scope = currentScope()
                    merges
                        .all(
                            scope.ownerId,
                            scope.serverOrigin,
                        ).filter { it.pending && resolve(scope, it.destinationId) == journalId }
                        .forEach { row ->
                            val retained = recoveryContents(scope, row)
                            merges.put(row.copy(recoveryContentIds = Json.encodeToString(retained.map { it.toString() })))
                        }
                }
                journalDao.delete(journalId)
            }
        }

    override suspend fun updateSyncMetadata(
        journalId: Uuid,
        syncVersion: Long,
        syncedAt: Instant,
    ) = withContext(dispatcher) {
        journalDao.updateSyncMetadata(journalId, syncVersion, syncedAt)
    }

    private suspend fun resolveDraft(draft: EditorDraft): EditorDraft =
        draft.copy(selectedJournalIds = draft.selectedJournalIds.map { resolveJournalId(it) }.distinct())

    override suspend fun previewMerge(
        sourceId: Uuid,
        destinationId: Uuid,
    ): JournalMergePreview? = transactionManager.withTransaction { readPreview(currentScope(), sourceId, destinationId) }

    private suspend fun readPreview(
        scope: JournalMergeScope,
        sourceId: Uuid,
        destinationId: Uuid,
    ): JournalMergePreview? {
        if (sourceId == destinationId || resolve(scope, sourceId) != sourceId || resolve(scope, destinationId) != destinationId) return null
        val source = journalDao.getJournalById(sourceId) ?: return null
        val destination = journalDao.getJournalById(destinationId) ?: return null
        return JournalMergePreview(
            source.toModel(),
            destination.toModel(),
            links.getContentForJournal(sourceId).first().toSet(),
            links.getContentForJournal(destinationId).first().toSet(),
        )
    }

    override suspend fun merge(
        preview: JournalMergePreview,
        operationId: Uuid,
    ): JournalMergeResult {
        val scope = currentScope()
        return transactionManager.withTransaction {
            val prior = merges.all(scope.ownerId, scope.serverOrigin).firstOrNull { it.operationId == operationId }
            if (prior != null) {
                check(
                    prior.sourceId == preview.source.id && prior.requestedDestinationId == preview.destination.id,
                ) { "Merge operation ID reused" }
                return@withTransaction JournalMergeResult.Merged(prior.toOperation())
            }
            val current =
                readPreview(scope, preview.source.id, preview.destination.id) ?: return@withTransaction JournalMergeResult.Unavailable
            if (current != preview) return@withTransaction JournalMergeResult.ReviewChanged(current)
            val now = Clock.System.now().toEpochMilliseconds()
            val row = preview.toPendingMergeEntity(scope, operationId, now)
            transfer(scope, row.sourceId, row.destinationId, queueLinks = false)
            merges.put(row)
            queue(row)
            suppressSourceOutbox(scope, row.sourceId)
            journalDao.delete(row.sourceId)
            check(currentScope() == scope) { "Journal merge scope changed" }
            JournalMergeResult.Merged(row.toOperation())
        }
    }

    override suspend fun resolveJournalId(journalId: Uuid): Uuid = if (database == null) journalId else resolve(currentScope(), journalId)

    private suspend fun resolve(
        scope: JournalMergeScope,
        journalId: Uuid,
    ): Uuid {
        val visited = mutableSetOf<Uuid>()
        var current = journalId
        while (true) {
            check(visited.add(current)) { "Journal merge redirect cycle" }
            current = merges.find(scope.ownerId, scope.serverOrigin, current)?.destinationId ?: return current
        }
    }

    override suspend fun applyJournalRedirect(
        sourceId: Uuid,
        destinationId: Uuid,
        expectedScope: JournalMergeScope?,
    ) {
        val scope = expectedScope ?: currentScope()
        transactionManager.withTransaction {
            check(currentScope() == scope) { "Journal merge scope changed" }
            val destination = resolve(scope, destinationId)
            check(destination != sourceId) { "Journal merge redirect cycle" }
            val existing = merges.find(scope.ownerId, scope.serverOrigin, sourceId)
            val oldDestination = existing?.let { resolve(scope, it.destinationId) }
            val superseded = existing?.pending == true && oldDestination != destination
            val oldDestinationMissing = oldDestination?.let { journalDao.getJournalById(it) == null } == true
            val retained =
                if (superseded) recoveryContents(scope, existing!!) else existing?.takeUnless { it.pending }?.recoveryContents().orEmpty()
            val row =
                existing?.redirected(destination, retained, superseded) ?: journalRedirectEntity(
                    scope,
                    sourceId,
                    destination,
                    journalDao.getJournalById(sourceId)?.title.orEmpty(),
                    Clock.System.now().toEpochMilliseconds(),
                )
            merges.put(row)
            if (superseded) {
                outbox.deletePending(scope.ownerId, scope.serverOrigin, "JOURNAL_MERGE", row.operationId.toString())
                outbox.getPendingByType(scope.ownerId, scope.serverOrigin, "ASSOCIATION").forEach { pending ->
                    val key = AssociationPendingKey.fromPendingId(pending.entityId)
                    if (oldDestinationMissing &&
                        pending.operation == "CREATE" &&
                        key != null &&
                        key.journalId == oldDestination &&
                        key.contentId in retained
                    ) {
                        settle(pending)
                    }
                }
            }
            if (journalDao.getJournalById(destination) != null) {
                transfer(scope, sourceId, destination, queueLinks = true, extraContentIds = retained)
                if (!row.pending) merges.put(row.copy(recoveryContentIds = "[]"))
                suppressSourceOutbox(scope, sourceId)
                journalDao.delete(sourceId)
            }
            check(currentScope() == scope) { "Journal merge scope changed" }
        }
    }

    override suspend fun reconcileJournalRedirects(expectedScope: JournalMergeScope?) {
        val scope = expectedScope ?: currentScope()
        merges.all(scope.ownerId, scope.serverOrigin).forEach { applyJournalRedirect(it.sourceId, it.destinationId, scope) }
    }

    private suspend fun transfer(
        scope: JournalMergeScope,
        sourceId: Uuid,
        destinationId: Uuid,
        queueLinks: Boolean,
        extraContentIds: Set<Uuid> = emptySet(),
    ) {
        for (id in (links.getContentForJournal(sourceId).first() + extraContentIds).distinct()) {
            links.addContentToJournal(JournalContentEntityLink(destinationId, id))
            val key = AssociationPendingKey(destinationId, id).toPendingId()
            val pending = outbox.getPending(scope.ownerId, scope.serverOrigin, "ASSOCIATION", key)
            if (pending?.operation == "DELETE") settle(pending)
            if (queueLinks && (pending == null || pending.operation == "DELETE")) {
                outbox.insertPending(scope.toPendingAssociation(destinationId, id, Clock.System.now().toEpochMilliseconds()))
            }
        }
    }

    private suspend fun suppressSourceOutbox(
        scope: JournalMergeScope,
        sourceId: Uuid,
    ) {
        outbox.deletePending(scope.ownerId, scope.serverOrigin, "JOURNAL", sourceId.toString())
        outbox
            .getPendingByType(scope.ownerId, scope.serverOrigin, "ASSOCIATION")
            .filter {
                AssociationPendingKey.fromPendingId(it.entityId)?.journalId == sourceId
            }.forEach { outbox.deletePending(scope.ownerId, scope.serverOrigin, "ASSOCIATION", it.entityId) }
    }

    private suspend fun settle(pending: PendingUploadEntity) =
        outbox.deletePendingIfCurrent(pending.ownerId, pending.serverOrigin, pending.entityType, pending.entityId, pending.operationId)

    private suspend fun queue(row: JournalMergeEntity) = outbox.insertPending(row.toPendingUpload())

    private suspend fun recoveryContents(
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

    override suspend fun getJournalMerge(operationId: Uuid): JournalMergeOperation? {
        val scope = currentScope()
        return merges
            .all(scope.ownerId, scope.serverOrigin)
            .firstOrNull {
                it.operationId == operationId ||
                    operationId.toString() in Json.decodeFromString<List<String>>(it.previousOperationIds)
            }?.toOperation()
    }

    override suspend fun previewPendingMerge(
        operationId: Uuid,
        destinationId: Uuid,
    ): JournalMergePreview? =
        transactionManager.withTransaction {
            val scope = currentScope()
            val row =
                merges.all(scope.ownerId, scope.serverOrigin).firstOrNull { it.pending && it.operationId == operationId }
                    ?: return@withTransaction null
            if (resolve(scope, destinationId) != destinationId || destinationId == row.sourceId) return@withTransaction null
            val destination = journalDao.getJournalById(destinationId)?.toModel() ?: return@withTransaction null
            val operation = row.toOperation()
            JournalMergePreview(
                operation.source ?: Journal(id = row.sourceId, title = row.sourceTitle),
                destination,
                recoveryContents(scope, row),
                links.getContentForJournal(destinationId).first().toSet(),
            )
        }

    override suspend fun pendingJournalMerges(): List<JournalMergeOperation> {
        val scope = currentScope()
        return merges.all(scope.ownerId, scope.serverOrigin).filter { it.pending }.map { it.toOperation() }
    }

    override fun observeJournalMergeIssues(): Flow<List<JournalMergeOperation>> =
        mergeChanges.map {
            val scope = currentScope()
            merges.all(scope.ownerId, scope.serverOrigin).filter { it.pending && it.needsDestination }.map { it.toOperation() }
        }

    override suspend fun markJournalMergeNeedsDestination(operation: JournalMergeOperation) {
        if (currentScope() != operation.scope) return
        transactionManager.withTransaction {
            val row = merges.find(operation.scope.ownerId, operation.scope.serverOrigin, operation.sourceId) ?: return@withTransaction
            if (row.operationId == operation.operationId && row.pending) {
                merges.put(
                    row.copy(
                        needsDestination = true,
                        recoveryContentIds = Json.encodeToString(recoveryContents(operation.scope, row).map { it.toString() }),
                    ),
                )
            }
        }
    }

    override suspend fun markJournalMergeSynced(operation: JournalMergeOperation) {
        if (currentScope() != operation.scope) return
        transactionManager.withTransaction {
            val row = merges.find(operation.scope.ownerId, operation.scope.serverOrigin, operation.sourceId) ?: return@withTransaction
            if (row.operationId != operation.operationId) return@withTransaction
            merges.put(row.copy(pending = false, needsDestination = false, recoveryContentIds = "[]"))
            settle(row.toPendingUpload())
        }
    }

    override suspend fun retargetPendingMerge(
        operationId: Uuid,
        preview: JournalMergePreview,
        replacementOperationId: Uuid,
    ): JournalMergeResult {
        val scope = currentScope()
        return transactionManager.withTransaction {
            merges.all(scope.ownerId, scope.serverOrigin).firstOrNull { it.operationId == replacementOperationId }?.let { prior ->
                return@withTransaction if (prior.sourceId == preview.source.id && prior.requestedDestinationId == preview.destination.id) {
                    JournalMergeResult.Merged(prior.toOperation())
                } else {
                    JournalMergeResult.Unavailable
                }
            }
            val row =
                merges.all(scope.ownerId, scope.serverOrigin).firstOrNull {
                    it.pending &&
                        it.needsDestination &&
                        it.operationId == operationId
                }
                    ?: return@withTransaction JournalMergeResult.Unavailable
            val destination = preview.destination.id
            if (resolve(scope, destination) != destination ||
                destination == row.sourceId
            ) {
                return@withTransaction JournalMergeResult.Unavailable
            }
            val currentDestination =
                journalDao.getJournalById(destination)?.toModel() ?: return@withTransaction JournalMergeResult.Unavailable
            val operation = row.toOperation()
            val current =
                JournalMergePreview(
                    operation.source ?: Journal(id = row.sourceId, title = row.sourceTitle),
                    currentDestination,
                    recoveryContents(scope, row),
                    links.getContentForJournal(destination).first().toSet(),
                )
            if (current != preview) return@withTransaction JournalMergeResult.ReviewChanged(current)
            val updated = row.retargeted(current, replacementOperationId)
            transfer(scope, row.sourceId, destination, queueLinks = false, extraContentIds = current.sourceContentIds)
            val oldDestination = resolve(scope, row.destinationId)
            outbox.getPendingByType(scope.ownerId, scope.serverOrigin, "ASSOCIATION").forEach { pending ->
                val key = AssociationPendingKey.fromPendingId(pending.entityId)
                if (pending.operation == "CREATE" && key?.journalId == oldDestination && key.contentId in current.sourceContentIds) {
                    settle(pending)
                }
            }
            merges.put(updated)
            outbox.deletePending(scope.ownerId, scope.serverOrigin, "JOURNAL_MERGE", operationId.toString())
            queue(updated)
            check(currentScope() == scope) { "Journal merge scope changed" }
            JournalMergeResult.Merged(updated.toOperation())
        }
    }
}
