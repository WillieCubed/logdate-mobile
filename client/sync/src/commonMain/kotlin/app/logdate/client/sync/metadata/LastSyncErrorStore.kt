package app.logdate.client.sync.metadata

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.SyncError
import app.logdate.client.sync.SyncErrorType

/**
 * Keeps the last sync error across app restarts. Only the category is retained; free-form messages and causes can contain private data.
 */
interface LastSyncErrorStore {
    suspend fun load(): SyncError?

    /** Saves [error], or forgets the saved one when it is null. */
    suspend fun save(error: SyncError?)
}

class InMemoryLastSyncErrorStore : LastSyncErrorStore {
    private var error: SyncError? = null

    override suspend fun load(): SyncError? = error

    override suspend fun save(error: SyncError?) {
        this.error = error?.copy(message = error.type.name, cause = null)
    }
}

class KeyValueLastSyncErrorStore(
    private val storage: KeyValueStorage,
) : LastSyncErrorStore {
    override suspend fun load(): SyncError? {
        val type = storage.getString(KEY_TYPE)?.let { name -> SyncErrorType.entries.firstOrNull { it.name == name } } ?: return null
        storage.remove(KEY_MESSAGE)
        return SyncError(type = type, message = type.name)
    }

    override suspend fun save(error: SyncError?) {
        if (error == null) {
            storage.remove(KEY_TYPE)
            storage.remove(KEY_MESSAGE)
            return
        }
        storage.putString(KEY_TYPE, error.type.name)
        storage.remove(KEY_MESSAGE)
    }

    private companion object {
        const val KEY_TYPE = "sync_last_error_type"
        const val KEY_MESSAGE = "sync_last_error_message"
    }
}
