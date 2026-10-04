package app.logdate.client.sync

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.metadata.UploadScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** New builds reconsider retained work once per account and server; ordinary relaunches keep backoff. */
internal class SyncUpgradeResumption(
    private val storage: KeyValueStorage,
) {
    private val mutex = Mutex()

    suspend fun resume(
        version: String,
        scope: UploadScope,
        isCurrent: () -> Boolean,
        release: suspend () -> Unit,
    ) = mutex.withLock {
        require(version.isNotBlank() && scope.ownerId.isNotBlank() && scope.serverOrigin.isNotBlank())
        check(isCurrent()) { "Sync scope changed" }
        val key = "sync_resume_build_v1_${scope.ownerId.length}:${scope.ownerId}:${scope.serverOrigin.length}:${scope.serverOrigin}"
        if (storage.getString(key) == version) return@withLock
        release()
        check(isCurrent()) { "Sync scope changed" }
        storage.putString(key, version)
    }
}
