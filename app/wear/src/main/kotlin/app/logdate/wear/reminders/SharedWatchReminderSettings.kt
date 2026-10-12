package app.logdate.wear.reminders

import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.StampedPreferenceStore
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.StateDocument
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

class SharedWatchReminderSettings(
    private val shared: StampedPreferenceStore,
    private val local: WatchReminderPreferences,
    private val acceptsPhone: suspend (String) -> Boolean,
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()

    suspend fun initialize() =
        mutex.withLock {
            val key = SharedPreferenceKey.RemindersEnabled
            val existing = shared.snapshot()[key.name]
            if (existing == null && local.enabled.value) {
                shared.write(key.name, key.encode(true))
            } else if (existing != null) {
                local.setEnabled(key.read(existing))
            }
        }

    suspend fun persistChoice(enabled: Boolean) =
        mutex.withLock {
            val key = SharedPreferenceKey.RemindersEnabled
            shared.write(key.name, key.encode(enabled))
            Unit
        }

    suspend fun adopt(
        node: String,
        document: StateDocument,
    ): Boolean =
        mutex.withLock {
            if (document.role != PeerRole.PHONE || !acceptsPhone(node)) return@withLock false
            shared.adopt(document.shared)
            if (!acceptsPhone(node)) return@withLock false
            document.lastReminderAt?.let { local.mergePhoneReminderAt(node, it, clock.now()) }
            val key = SharedPreferenceKey.RemindersEnabled
            val enabled = key.read(shared.snapshot()[key.name])
            if (enabled == local.enabled.value) return@withLock false
            local.setEnabled(enabled)
            true
        }
}
