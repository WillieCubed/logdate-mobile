package app.logdate.client.data.journals

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.repository.journals.DraftRepository
import app.logdate.shared.config.LogDateConfigRepository
import app.logdate.shared.model.EditorDraft
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** A signed-in account and server that owns a locally saved editor draft. */
data class DraftStorageScope(
    val ownerId: String,
    val serverOrigin: String,
)

/** Stores editor drafts locally and binds pre-scoping drafts to the first signed-in account. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LocalFirstDraftRepository(
    private val keyValueStorage: KeyValueStorage,
    private val json: Json,
    private val currentScope: (() -> DraftStorageScope?)? = null,
    private val scopeChanges: Flow<DraftStorageScope?>? = null,
) : DraftRepository {
    constructor(
        keyValueStorage: KeyValueStorage,
        json: Json,
        sessionStorage: SessionStorage,
        configRepository: LogDateConfigRepository,
    ) : this(
        keyValueStorage,
        json,
        currentScope = { boundDraftScope(sessionStorage, configRepository) },
        scopeChanges =
            combine(sessionStorage.getSessionFlow(), configRepository.backendUrl) { _, _ ->
                boundDraftScope(sessionStorage, configRepository)
            },
    )

    private companion object {
        const val LEGACY_KEY = "editor_drafts"
        const val LEGACY_OWNER_KEY = "editor_drafts_legacy_owner"
        const val ANONYMOUS_KEY = "editor_drafts_anonymous"
        val storageMutex = Mutex()
    }

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

    private fun decode(value: String?): List<EditorDraft> = if (value.isNullOrEmpty()) emptyList() else json.decodeFromString(value)

    /** The owner marker is durable before copying; a failed copy resumes only for that owner. */
    private suspend fun migrateLegacy(scope: DraftStorageScope?) {
        if (currentScope == null || scope == null) return
        require(scope.ownerId.isNotBlank() && scope.serverOrigin.isNotBlank())
        val legacy = keyValueStorage.getString(LEGACY_KEY)?.takeIf { it.isNotEmpty() } ?: return
        val expectedOwner = ownerMarker(scope)
        val owner = keyValueStorage.getString(LEGACY_OWNER_KEY)
        if (owner == null) {
            keyValueStorage.putString(LEGACY_OWNER_KEY, expectedOwner)
        } else if (owner != expectedOwner) {
            return
        }
        val doneKey = "${scopedKey(scope)}_legacy_copied"
        if (keyValueStorage.getBoolean(doneKey, false)) return
        val scoped = decode(keyValueStorage.getString(scopedKey(scope)))
        val existingIds = scoped.map { it.id }.toSet()
        val merged = decode(legacy).filterNot { it.id in existingIds } + scoped
        keyValueStorage.putString(scopedKey(scope), json.encodeToString(merged))
        keyValueStorage.putBoolean(doneKey, true)
    }

    private suspend fun read(scope: DraftStorageScope?): List<EditorDraft> {
        migrateLegacy(scope)
        val drafts = decode(keyValueStorage.getString(storageKey(scope)))
        check(currentScope?.invoke() == scope) { "Draft account changed during read" }
        return drafts
    }

    private suspend fun write(
        scope: DraftStorageScope?,
        drafts: List<EditorDraft>,
    ) {
        check(currentScope?.invoke() == scope) { "Draft account changed during write" }
        keyValueStorage.putString(storageKey(scope), json.encodeToString(drafts))
        check(currentScope?.invoke() == scope) { "Draft account changed during write" }
    }

    override suspend fun saveDraft(draft: EditorDraft) {
        try {
            storageMutex.withLock {
                val scope = currentScope?.invoke()
                val drafts = read(scope).toMutableList()
                val index = drafts.indexOfFirst { it.id == draft.id }
                if (index >= 0) {
                    drafts[index] = draft.copy(lastModifiedAt = Clock.System.now())
                } else {
                    drafts.add(draft)
                }
                write(scope, drafts)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Napier.e("Draft storage save failed")
        }
    }

    override suspend fun saveDraftFromSync(draft: EditorDraft) {
        storageMutex.withLock {
            val scope = currentScope?.invoke()
            val drafts = read(scope).toMutableList()
            val index = drafts.indexOfFirst { it.id == draft.id }
            if (index >= 0) drafts[index] = draft else drafts.add(draft)
            write(scope, drafts)
        }
    }

    override suspend fun getLatestDraft(): EditorDraft? = getAllDrafts().maxByOrNull { it.lastModifiedAt }

    override suspend fun getAllDrafts(): List<EditorDraft> =
        try {
            storageMutex.withLock { read(currentScope?.invoke()) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Napier.e("Draft storage read failed")
            emptyList()
        }

    override suspend fun getAllDraftsForSync(): List<EditorDraft> = storageMutex.withLock { read(currentScope?.invoke()) }

    override val allDrafts: Flow<List<EditorDraft>> =
        if (scopeChanges == null) {
            keyValueStorage.observeString(storageKey(currentScope?.invoke())).map { value ->
                runCatching { decode(value) }.getOrDefault(emptyList())
            }
        } else {
            scopeChanges.flatMapLatest { scope ->
                flow {
                    val selected = currentScope?.invoke()
                    if (selected != scope) {
                        emit(emptyList())
                    } else {
                        storageMutex.withLock { migrateLegacy(scope) }
                        emitAll(
                            keyValueStorage.observeString(storageKey(scope)).map { value ->
                                if (currentScope?.invoke() != scope) {
                                    emptyList()
                                } else {
                                    runCatching { decode(value) }.getOrDefault(emptyList())
                                }
                            },
                        )
                    }
                }
            }
        }

    override suspend fun getDraft(id: Uuid): EditorDraft? = getAllDrafts().find { it.id == id }

    override suspend fun deleteDraft(id: Uuid) {
        try {
            storageMutex.withLock {
                val scope = currentScope?.invoke()
                val drafts = read(scope).filterNot { it.id == id }
                write(scope, drafts)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Napier.e("Draft storage delete failed")
        }
    }

    override suspend fun clearAllDrafts() {
        try {
            storageMutex.withLock { write(currentScope?.invoke(), emptyList()) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Napier.e("Draft storage clear failed")
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
