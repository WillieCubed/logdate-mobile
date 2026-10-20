package app.logdate.client.data.journals

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.repository.journals.DraftRecoveryScope
import app.logdate.client.repository.journals.DraftRepository
import app.logdate.client.sync.metadata.PendingOperation
import app.logdate.client.sync.metadata.SyncMetadataService
import app.logdate.client.sync.withTransactionCompletion
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.EditorDraft
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** A signed-in account and server that owns a locally saved editor draft. */
data class DraftStorageScope(
    val ownerId: String,
    val serverOrigin: String,
)

/** Stores local drafts independently of login, with separate visibility and selected-server custody. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalFirstDraftRepository(
    private val keyValueStorage: KeyValueStorage,
    private val json: Json,
    private val currentScope: (() -> DraftStorageScope?)? = null,
    private val scopeChanges: Flow<DraftStorageScope?>? = null,
    private val canonicalOwner: CanonicalOwnerProvider? = null,
    private val metadata: SyncMetadataService? = null,
    private val canonicalScope: (suspend () -> DraftStorageScope?)? = null,
    private val accessAllowed: suspend () -> Boolean = { true },
    private val transactions: app.logdate.client.sync.SyncTransactionManager? = null,
) : DraftRepository {
    constructor(
        keyValueStorage: KeyValueStorage,
        json: Json,
        sessionStorage: SessionStorage,
        configRepository: LogDateConfigRepository,
        canonicalOwner: CanonicalOwnerProvider? = null,
        metadata: SyncMetadataService? = null,
        configurationReady: suspend () -> Unit = {},
        transactions: app.logdate.client.sync.SyncTransactionManager? = null,
    ) : this(
        keyValueStorage,
        json,
        currentScope = { boundDraftScope(sessionStorage, configRepository) },
        scopeChanges =
            combine(sessionStorage.getSessionFlow(), configRepository.backendUrl) { _, _ ->
                boundDraftScope(sessionStorage, configRepository)
            },
        canonicalOwner = canonicalOwner,
        transactions = transactions,
        metadata = metadata,
        canonicalScope =
            canonicalOwner?.let { owner ->
                suspend {
                    configurationReady()
                    if (owner.hasBoundOwner()) {
                        DraftStorageScope(owner.getCanonicalOwnerId(), configRepository.getCurrentBackendUrl().trimEnd('/'))
                    } else {
                        boundDraftScope(sessionStorage, configRepository)
                    }
                }
            },
        accessAllowed = {
            configurationReady()
            canonicalOwner == null ||
                !canonicalOwner.hasBoundOwner() ||
                sessionStorage.getSession()?.accountId.let { it == null || it == canonicalOwner.getCanonicalOwnerId() }
        },
    )

    internal companion object {
        const val LEGACY_KEY = "editor_drafts"
        const val LEGACY_OWNER_KEY = "editor_drafts_legacy_owner"
        const val ANONYMOUS_KEY = "editor_drafts_anonymous"
        val storageMutex = Mutex()
    }

    private val effectiveTransactions = transactions ?: DirectArchiveRecoveryTransactions

    private val custody =
        DraftCustodyStore(keyValueStorage, json, metadata, effectiveTransactions, canonicalOwner) { scope ->
            check(accessAllowed() && selectedScope() == scope) { "Draft account changed during custody transfer" }
        }

    private val mutations = DraftMutationStore(keyValueStorage, json, metadata, effectiveTransactions)

    private suspend fun <T> withStorage(block: suspend () -> T): T =
        effectiveTransactions.withTransactionCompletion { storageMutex.withLock { block() } }

    private suspend fun flushMutations(
        key: String,
        scope: DraftStorageScope?,
    ) {
        mutations.flush(key) { receipt ->
            if (!accessAllowed() || selectedScope() != scope) {
                false
            } else {
                if (scope != null) custody.completeSelectedSources(scope, key, receipt.entityId)
                true
            }
        }
    }

    private suspend fun selectedScope(): DraftStorageScope? = canonicalScope?.invoke() ?: currentScope?.invoke()

    private fun scopedKey(scope: DraftStorageScope): String =
        "editor_drafts_scope_${scope.ownerId.length}:${scope.ownerId}:${scope.serverOrigin.length}:${scope.serverOrigin}"

    private fun storageKey(scope: DraftStorageScope?): String =
        when {
            currentScope == null -> LEGACY_KEY
            scope == null -> ANONYMOUS_KEY
            else -> scopedKey(scope)
        }

    private fun ownerMarker(scope: DraftStorageScope): String =
        "${scope.ownerId.length}:${scope.ownerId}:${scope.serverOrigin.length}:${scope.serverOrigin}"

    private fun decode(value: String?): List<EditorDraft> = ArchiveDraftStorageState.decode(json, value).drafts

    /** The owner marker is durable before copying; a failed copy resumes only for that owner. */
    private suspend fun migrateLegacy(scope: DraftStorageScope?) {
        if (currentScope == null || scope == null) return
        require(scope.ownerId.isNotBlank() && scope.serverOrigin.isNotBlank())
        val legacy = keyValueStorage.getStringOrThrow(LEGACY_KEY)?.takeIf { it.isNotEmpty() } ?: return
        val expectedOwner = ownerMarker(scope)
        val owner = keyValueStorage.getStringOrThrow(LEGACY_OWNER_KEY)
        if (owner == null) {
            keyValueStorage.putStringOrThrow(LEGACY_OWNER_KEY, expectedOwner)
        } else if (owner != expectedOwner) {
            return
        }
        val doneKey = "${scopedKey(scope)}_legacy_copied"
        if (keyValueStorage.getBoolean(doneKey, false)) return
        val scoped = decode(keyValueStorage.getStringOrThrow(scopedKey(scope)))
        val existingIds = scoped.map { it.id }.toSet()
        val merged = decode(legacy).filterNot { it.id in existingIds } + scoped
        val receipts =
            ArchiveDraftStorageState.decode(json, legacy).appliedRecoveries +
                ArchiveDraftStorageState.decode(json, keyValueStorage.getStringOrThrow(scopedKey(scope))).appliedRecoveries
        keyValueStorage.putStringOrThrow(scopedKey(scope), ArchiveDraftStorageState(merged, receipts).encode(json))
        keyValueStorage.putBoolean(doneKey, true)
    }

    private suspend fun read(scope: DraftStorageScope?): List<EditorDraft> {
        if (!accessAllowed()) return emptyList()
        flushMutations(storageKey(scope), scope)
        if (canonicalOwner == null) {
            migrateLegacy(scope)
        } else if (scope != null) {
            custody.reconcile(scope, storageKey(scope))
        }
        val drafts = decode(keyValueStorage.getStringOrThrow(storageKey(scope)))
        check(selectedScope() == scope) { "Draft account changed during read" }
        return drafts
    }

    private suspend fun write(
        scope: DraftStorageScope?,
        drafts: List<EditorDraft>,
        receipt: String? = null,
    ) {
        check(accessAllowed() && selectedScope() == scope) { "Draft account changed during write" }
        val state = ArchiveDraftStorageState.decode(json, keyValueStorage.getStringOrThrow(storageKey(scope)))
        val receipts = state.appliedRecoveries + listOfNotNull(receipt)
        val copies =
            if (scope == null) {
                state.custodyCopies
            } else {
                state.custodyCopies.filter { copy ->
                    drafts.any { it.id.toString() == copy.destinationId }
                }
            }
        keyValueStorage.putStringOrThrow(
            storageKey(scope),
            state.copy(drafts = drafts, appliedRecoveries = receipts, custodyCopies = copies).encode(json),
        )
        check(accessAllowed() && selectedScope() == scope) { "Draft account changed during write" }
    }

    private suspend fun visible(scope: DraftStorageScope?): List<EditorDraft> {
        val scoped = read(scope)
        return if (canonicalOwner != null && scope != null && accessAllowed()) custody.visible(scope, scoped, storageKey(scope)) else scoped
    }

    override suspend fun saveDraft(draft: EditorDraft) {
        withStorage {
            val scope = selectedScope()
            visible(scope)
            val drafts = read(scope).toMutableList()
            val index = drafts.indexOfFirst { it.id == draft.id }
            if (index >= 0) drafts[index] = draft.copy(lastModifiedAt = Clock.System.now()) else drafts.add(draft)
            write(scope, drafts)
            if (canonicalOwner != null && scope != null) custody.remove(scope, setOf(draft.id))
        }
    }

    override suspend fun persistDraftMutation(
        id: Uuid,
        replacement: EditorDraft?,
        scope: DraftRecoveryScope,
    ): Boolean =
        withStorage {
            if (canonicalOwner == null || metadata == null) return@withStorage false
            require(replacement == null || replacement.id == id)
            val selected = selectedScope()
            check(accessAllowed() && selected?.ownerId == scope.ownerId && selected?.serverOrigin == scope.serverOrigin) {
                "Draft recovery account changed"
            }
            visible(selected)
            val drafts = read(selected)
            val saved =
                replacement?.let { draft ->
                    if (drafts.any { it.id == id }) draft.copy(lastModifiedAt = Clock.System.now()) else draft
                }
            val key = storageKey(selected)
            val state = ArchiveDraftStorageState.decode(json, keyValueStorage.getStringOrThrow(key))
            val operation = if (saved == null) PendingOperation.DELETE else PendingOperation.UPDATE
            val prepared =
                mutations.prepare(
                    state.copy(drafts = drafts.filterNot { it.id == id } + listOfNotNull(saved)),
                    id,
                    operation,
                    scope,
                )
            val durable = if (selected == null) prepared else custody.captureSelectedSources(prepared, id)
            check(accessAllowed() && selectedScope() == selected) { "Draft account changed during write" }
            keyValueStorage.putStringOrThrow(key, durable.encode(json))
            // The captured upload intent survives a later session/server switch and SQL rollback.
            flushMutations(key, selected)
            true
        }

    override suspend fun saveDraftFromSync(draft: EditorDraft) {
        withStorage {
            val scope = selectedScope()
            val drafts = read(scope).toMutableList()
            val index = drafts.indexOfFirst { it.id == draft.id }
            if (index >= 0) drafts[index] = draft else drafts.add(draft)
            write(scope, drafts)
        }
    }

    override suspend fun getLatestDraft(): EditorDraft? = getAllDrafts().maxByOrNull { it.lastModifiedAt }

    override suspend fun getAllDrafts(): List<EditorDraft> =
        try {
            withStorage { visible(selectedScope()) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Napier.e("Draft storage read failed")
            emptyList()
        }

    override suspend fun getAllDraftsForSync(): List<EditorDraft> = withStorage { read(selectedScope()) }

    override val allDrafts: Flow<List<EditorDraft>> =
        (scopeChanges ?: flowOf(null)).flatMapLatest {
            flow {
                val scope = selectedScope()
                val observations =
                    listOf(
                        keyValueStorage.observeString(storageKey(scope)),
                        keyValueStorage.observeString(ANONYMOUS_KEY),
                        keyValueStorage.observeString(LEGACY_KEY),
                    )
                combine(observations) { getAllDrafts() }.collect { emit(it) }
            }
        }

    override suspend fun hasUnclaimedDraft(id: Uuid): Boolean =
        withStorage {
            canonicalOwner != null && accessAllowed() && custody.contains(id)
        }

    override suspend fun getDraft(id: Uuid): EditorDraft? = getAllDrafts().find { it.id == id }

    override suspend fun getDraftForRestore(id: Uuid): EditorDraft? =
        withStorage {
            visible(selectedScope()).find {
                it.id ==
                    id
            }
        }

    override suspend fun getDraftRecoveryScope(): DraftRecoveryScope =
        selectedScope()?.let { DraftRecoveryScope(it.ownerId, it.serverOrigin) } ?: DraftRecoveryScope(null, null)

    override suspend fun hasArchiveRecoveryDecision(
        id: Uuid,
        recoveryScope: DraftRecoveryScope?,
        recoveryId: String,
    ): Boolean =
        withStorage {
            val scope = selectedScope()
            check(recoveryScope == null || scope?.ownerId == recoveryScope.ownerId && scope?.serverOrigin == recoveryScope.serverOrigin) {
                "Draft recovery account changed"
            }
            val state = ArchiveDraftStorageState.decode(json, keyValueStorage.getStringOrThrow(storageKey(scope)))
            check(selectedScope() == scope) { "Draft recovery account changed" }
            "$recoveryId:$id" in state.appliedRecoveries
        }

    override suspend fun compareAndSetDraftForRestore(
        id: Uuid,
        expected: EditorDraft?,
        replacement: EditorDraft?,
        recoveryScope: DraftRecoveryScope?,
        recoveryId: String?,
    ): Boolean =
        withStorage {
            require(expected == null || expected.id == id)
            require(replacement == null || replacement.id == id)
            val scope = selectedScope()
            check(recoveryScope == null || scope?.ownerId == recoveryScope.ownerId && scope?.serverOrigin == recoveryScope.serverOrigin) {
                "Draft recovery account changed"
            }
            val drafts = visible(scope)
            val scoped = read(scope)
            val receipt = recoveryId?.let { "$it:$id" }
            val state = ArchiveDraftStorageState.decode(json, keyValueStorage.getStringOrThrow(storageKey(scope)))
            if (receipt != null && receipt in state.appliedRecoveries) return@withStorage false
            if (drafts.find { it.id == id } != expected) {
                if (receipt != null) write(scope, scoped, receipt)
                return@withStorage false
            }
            if (expected == replacement && receipt == null) return@withStorage true
            val updated = if (expected == replacement) scoped else scoped.filterNot { it.id == id } + listOfNotNull(replacement)
            write(scope, updated, receipt)
            if (canonicalOwner != null && scope != null && expected != replacement) custody.remove(scope, setOf(id))
            true
        }

    override suspend fun deleteDraft(id: Uuid) {
        withStorage {
            val scope = selectedScope()
            visible(scope)
            val drafts = read(scope).filterNot { it.id == id }
            write(scope, drafts)
            if (canonicalOwner != null && scope != null) custody.remove(scope, setOf(id))
        }
    }

    override suspend fun clearAllDrafts() {
        withStorage {
            val scope = selectedScope()
            val ids = visible(scope).map { it.id }.toSet()
            write(scope, emptyList())
            if (canonicalOwner != null && scope != null) custody.remove(scope, ids)
        }
    }
}

private fun boundDraftScope(
    sessions: SessionStorage,
    config: LogDateConfigRepository,
): DraftStorageScope? {
    val bound = sessions.getOriginBoundSession() ?: return null
    if (bound.origin != config.getCurrentBackendUrl() || bound.session.accountId.isBlank()) return null
    return DraftStorageScope(bound.session.accountId, bound.origin.trimEnd('/'))
}
