package app.logdate.client.sync.metadata

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.cloud.CloudRequestBinding
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** Local-only content/key fingerprints are never sent to the server or diagnostics. */
@Serializable
private data class UploadIntent(
    val fingerprint: String,
    val id: String,
)

/** Persists intent BEFORE sending bytes. A changed owner, endpoint, content or key gets a new ID. */
class MediaUploadIntentStore(
    private val storage: KeyValueStorage,
    private val currentScope: suspend () -> UploadScope,
) {
    suspend fun getOrCreate(
        contentId: String,
        fingerprint: String,
    ): String {
        val binding = currentCoroutineContext()[CloudRequestBinding]
        val scope = binding?.let { UploadScope(it.session.accountId, it.location.origin) } ?: currentScope()
        require(scope.ownerId.isNotBlank() && scope.serverOrigin.isNotBlank()) { "Media upload ownership is unavailable" }
        val scopeKey = "${scope.ownerId.length}:${scope.ownerId}${scope.serverOrigin.length}:${scope.serverOrigin}"
        val key = "media_upload_intent_v1_$scopeKey:$contentId:$fingerprint"
        storage.getStringOrThrow(key)?.let {
            val saved = Json.decodeFromString<UploadIntent>(it)
            check(saved.fingerprint == fingerprint) { "Upload identity fingerprint mismatch" }
            return saved.id
        }
        val next = UploadIntent(fingerprint, Uuid.random().toString())
        if (storage.compareAndSetStringsOrThrow(mapOf(key to null), mapOf(key to Json.encodeToString(next)))) return next.id
        val winner =
            Json.decodeFromString<UploadIntent>(
                checkNotNull(storage.getStringOrThrow(key)) { "Concurrent upload identity disappeared" },
            )
        check(winner.fingerprint == fingerprint) { "Upload identity fingerprint mismatch" }
        return winner.id
    }

    suspend fun markReceiptSupported(id: String) {
        storage.putStringOrThrow("media_receipt_required_v1_$id", "true")
    }

    suspend fun receiptRequired(id: String): Boolean {
        val saved = storage.getStringOrThrow("media_receipt_required_v1_$id")
        check(saved == null || saved == "true") { "Upload receipt support marker is invalid" }
        return saved == "true"
    }

    /** Serialize only identical intents, including independent data-source/store instances. */
    suspend fun <T> withIntentLock(
        id: String,
        action: suspend () -> T,
    ): T {
        val entry = registryMutex.withLock { active.getOrPut(id) { IntentLock() }.also { it.users++ } }
        try {
            return entry.mutex.withLock { action() }
        } finally {
            withContext(NonCancellable) {
                registryMutex.withLock { if (--entry.users == 0) active.remove(id) }
            }
        }
    }

    private class IntentLock(
        val mutex: Mutex = Mutex(),
        var users: Int = 0,
    )

    private companion object {
        val registryMutex = Mutex()
        val active = mutableMapOf<String, IntentLock>()
    }
}
