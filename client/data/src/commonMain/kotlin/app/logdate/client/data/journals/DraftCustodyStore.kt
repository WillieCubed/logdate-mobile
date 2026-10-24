package app.logdate.client.data.journals

import app.logdate.client.data.account.HistoryOwnerAdoption
import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.sync.SyncTransactionManager
import app.logdate.client.sync.metadata.EntityType
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.metadata.UploadScope
import app.logdate.shared.model.EditorDraft
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import kotlin.uuid.Uuid

/** A source snapshot and its durable destination are committed together before source cleanup. */
@Serializable
internal data class DraftCustodyCopy(
    val sourceKey: String,
    val sourceId: String,
    val sourceFingerprint: String,
    val destinationId: String,
)

internal class DraftCustodyStore(
    private val storage: KeyValueStorage,
    private val json: Json,
    private val metadata: SyncMetadataService?,
    private val transactions: SyncTransactionManager,
    private val canonicalOwner: CanonicalOwnerProvider?,
    private val requireCurrent: suspend (DraftStorageScope) -> Unit,
) {
    private val sourceKeys = listOf(LocalFirstDraftRepository.ANONYMOUS_KEY, LocalFirstDraftRepository.LEGACY_KEY)

    private suspend fun read(key: String) = ArchiveDraftStorageState.decode(json, storage.getStringOrThrow(key))

    private suspend fun write(
        key: String,
        state: ArchiveDraftStorageState,
    ) {
        storage.putStringOrThrow(key, state.encode(json))
    }

    suspend fun reconcile(
        scope: DraftStorageScope,
        destinationKey: String,
    ) {
        val service = metadata ?: return
        val uploadScope = UploadScope(scope.ownerId, scope.serverOrigin)
        val adoptedFrom = completedAdoptionSource(scope)?.let { UploadScope(it, scope.serverOrigin) }
        for (sourceKey in sourceKeys) {
            val source = read(sourceKey)
            for (draft in source.drafts) {
                val current = service.getPendingUploadForScope(EntityType.DRAFT, draft.id.toString(), uploadScope)
                val prior = adoptedFrom?.let { service.getPendingUploadForScope(EntityType.DRAFT, draft.id.toString(), it) }
                val proof =
                    current?.operation != PendingOperation.DELETE &&
                        listOfNotNull(current, prior).any {
                            it.scope in listOfNotNull(uploadScope, adoptedFrom) && it.operation != PendingOperation.DELETE
                        }
                val destination = read(destinationKey)
                val copy = destination.custodyCopies.find { it.matches(sourceKey, draft) }
                val mappedId = copy?.destinationId ?: draft.id.toString()
                if (draft.id.toString() in destination.localDeletionIds || mappedId in destination.localDeletionIds) continue
                val explicit = destination.pendingMutations.any { it.entityId == copy?.destinationId }
                if (!explicit && (proof || copy != null)) transfer(scope, destinationKey, sourceKey, draft, uploadScope)
            }
        }
    }

    private suspend fun transfer(
        scope: DraftStorageScope,
        destinationKey: String,
        sourceKey: String,
        draft: EditorDraft,
        uploadScope: UploadScope,
    ) {
        requireCurrent(scope)
        val destination = read(destinationKey)
        val receipt = destination.custodyCopies.find { it.matches(sourceKey, draft) }
        val mappedId = receipt?.destinationId ?: draft.id.toString()
        if (draft.id.toString() in destination.localDeletionIds || mappedId in destination.localDeletionIds) return
        val existing = destination.drafts.find { it.id == draft.id }
        val id =
            receipt?.destinationId?.let(Uuid::parse)
                ?: if (existing == null || existing == draft) draft.id else Uuid.random()
        if (receipt == null) {
            val copy = draft.copy(id = id)
            write(
                destinationKey,
                destination.copy(
                    drafts = destination.drafts.filterNot { it.id == id } + copy,
                    custodyCopies = destination.custodyCopies + copyReceipt(sourceKey, draft, id),
                ),
            )
        }
        requireCurrent(scope)
        val service = checkNotNull(metadata)
        val retained = read(destinationKey).drafts.any { it.id == id }
        check(retained) { "Draft custody destination is unavailable; source retained" }
        if (service.getPendingUploadForScope(EntityType.DRAFT, id.toString(), uploadScope) == null) {
            service.enqueuePendingForScope(id.toString(), EntityType.DRAFT, PendingOperation.UPDATE, uploadScope)
        }
        requireCurrent(scope)
        transactions.afterCommit {
            LocalFirstDraftRepository.storageMutex.withLock {
                cleanupTransferredSource(scope, destinationKey, sourceKey, draft)
            }
        }
    }

    private suspend fun cleanupTransferredSource(
        scope: DraftStorageScope,
        destinationKey: String,
        sourceKey: String,
        draft: EditorDraft,
    ) {
        requireCurrent(scope)
        val destination = read(destinationKey)
        val receipt = destination.custodyCopies.find { it.matches(sourceKey, draft) } ?: return
        if (destination.drafts.none { it.id.toString() == receipt.destinationId } ||
            destination.pendingMutations.any { it.entityId == receipt.destinationId && it.operation == PendingOperation.DELETE.name }
        ) {
            return
        }
        val source = read(sourceKey)
        val remaining = source.drafts.filterNot { it == draft }
        val aliases = source.custodyCopies.filter { alias -> remaining.any { alias.matches(sourceKey, it) } }
        write(sourceKey, source.copy(drafts = remaining, custodyCopies = aliases))
        requireCurrent(scope)
        val completed = read(destinationKey)
        write(destinationKey, completed.copy(custodyCopies = completed.custodyCopies.filterNot { it.matches(sourceKey, draft) }))
    }

    /** Unknown historical origin remains local; display aliases never confer upload custody. */
    suspend fun visible(
        scope: DraftStorageScope,
        scoped: List<EditorDraft>,
        destinationKey: String,
    ): List<EditorDraft> {
        val destination = read(destinationKey)
        val result = scoped.toMutableList()
        for (key in sourceKeys) {
            requireCurrent(scope)
            var source = read(key)
            for (draft in source.drafts) {
                val cleanup = destination.custodyCopies.find { it.matches(key, draft) }
                if (cleanup != null &&
                    (
                        destination.drafts.any { it.id.toString() == cleanup.destinationId } ||
                            destination.pendingMutations.any { it.entityId == cleanup.destinationId }
                    )
                ) {
                    continue
                }
                val existing = result.find { it.id == draft.id }
                val alias = source.custodyCopies.find { it.matches(key, draft) }
                val mappedId = alias?.destinationId?.let(Uuid::parse) ?: draft.id
                if (draft.id.toString() in destination.localDeletionIds || mappedId.toString() in destination.localDeletionIds) continue
                val visibleId =
                    if (alias != null) {
                        mappedId
                    } else if (existing != null && existing != draft) {
                        Uuid.random()
                    } else {
                        draft.id
                    }
                if (visibleId != draft.id && alias == null) {
                    source = source.copy(custodyCopies = source.custodyCopies + copyReceipt(key, draft, visibleId))
                    write(key, source)
                    requireCurrent(scope)
                }
                if (result.none { it.id == visibleId }) result.add(draft.copy(id = visibleId))
            }
        }
        return result
    }

    suspend fun contains(id: Uuid): Boolean =
        sourceKeys.any { key ->
            val state = read(key)
            state.drafts.any { draft ->
                draft.id == id || state.custodyCopies.any { it.matches(key, draft) && it.destinationId == id.toString() }
            }
        }

    suspend fun remove(
        scope: DraftStorageScope,
        ids: Set<Uuid>,
    ) {
        for (key in sourceKeys) {
            requireCurrent(scope)
            val source = read(key)
            val remaining =
                source.drafts.filterNot { draft ->
                    val alias = source.custodyCopies.find { it.matches(key, draft) }
                    (alias?.destinationId?.let(Uuid::parse) ?: draft.id) in ids
                }
            if (remaining != source.drafts) {
                val receipts = source.custodyCopies.filter { receipt -> remaining.any { receipt.matches(key, it) } }
                write(key, source.copy(drafts = remaining, custodyCopies = receipts))
            }
            requireCurrent(scope)
        }
    }

    suspend fun captureSelectedSources(
        state: ArchiveDraftStorageState,
        id: Uuid,
    ): ArchiveDraftStorageState {
        val copies = state.custodyCopies.toMutableList()
        for (key in sourceKeys) {
            val source = read(key)
            for (draft in source.drafts) {
                val alias = source.custodyCopies.find { it.matches(key, draft) }
                if ((alias?.destinationId?.let(Uuid::parse) ?: draft.id) == id) {
                    copies.removeAll { it.matches(key, draft) }
                    copies.add(copyReceipt(key, draft, id))
                }
            }
        }
        return state.copy(custodyCopies = copies)
    }

    /** Called after SQL commit while holding the draft mutex; only captured source versions retire. */
    suspend fun completeSelectedSources(
        scope: DraftStorageScope,
        key: String,
        id: String,
    ) {
        requireCurrent(scope)
        val state = read(key)
        val copies = state.custodyCopies.filter { it.destinationId == id }
        for (sourceKey in sourceKeys) {
            val source = read(sourceKey)
            val remaining = source.drafts.filterNot { draft -> copies.any { it.matches(sourceKey, draft) } }
            if (remaining != source.drafts) {
                val aliases = source.custodyCopies.filter { alias -> remaining.any { alias.matches(sourceKey, it) } }
                write(sourceKey, source.copy(drafts = remaining, custodyCopies = aliases))
            }
            requireCurrent(scope)
        }
        val current = read(key)
        write(key, current.copy(custodyCopies = current.custodyCopies - copies.toSet()))
    }

    private suspend fun completedAdoptionSource(scope: DraftStorageScope): String? {
        val owner = canonicalOwner ?: return null
        if (!owner.hasBoundOwner() || owner.getCanonicalOwnerId() != scope.ownerId) return null
        return HistoryOwnerAdoption.completedSourceFor(storage, scope.ownerId, scope.serverOrigin)
    }

    private fun fingerprint(draft: EditorDraft): String =
        json
            .encodeToString(draft)
            .encodeUtf8()
            .sha256()
            .hex()

    private fun DraftCustodyCopy.matches(
        key: String,
        draft: EditorDraft,
    ): Boolean = sourceKey == key && sourceId == draft.id.toString() && sourceFingerprint == fingerprint(draft)

    private fun copyReceipt(
        key: String,
        draft: EditorDraft,
        id: Uuid,
    ) = DraftCustodyCopy(key, draft.id.toString(), fingerprint(draft), id.toString())
}
