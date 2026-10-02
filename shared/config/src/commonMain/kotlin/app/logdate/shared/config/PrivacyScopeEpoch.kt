package app.logdate.shared.config

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/** Private operational consent boundary; never included in diagnostic events or reports. */
interface PrivacyEpochStorage {
    suspend fun read(): String?

    suspend fun write(epoch: String)
}

class PrivacyScopeEpoch(
    private val storage: PrivacyEpochStorage,
) {
    private val mutex = Mutex()
    private val current = MutableStateFlow<String?>(null)
    private var initialized = false
    val value: StateFlow<String?> = current.asStateFlow()

    suspend fun initialize() = mutex.withLock { load() }

    suspend fun <T> transition(
        changed: Boolean,
        publish: suspend () -> T,
    ): T = transition({ changed }, publish)

    suspend fun <T> transition(
        changed: () -> Boolean,
        publish: suspend () -> T,
    ): T =
        mutex.withLock {
            load()
            if (changed() || current.value == null) rotate()
            publish()
        }

    private suspend fun load() {
        if (initialized) return
        try {
            val stored = storage.read()
            if (stored != null && runCatching { Uuid.parse(stored) }.isSuccess) current.value = stored else rotate()
            initialized = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            current.value = null
            throw IllegalStateException("PRIVACY_BOUNDARY_STORAGE_FAILED")
        }
    }

    private suspend fun rotate() {
        current.value = null
        try {
            val next = Uuid.random().toString()
            storage.write(next)
            current.value = next
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw IllegalStateException("PRIVACY_BOUNDARY_STORAGE_FAILED")
        }
    }
}
