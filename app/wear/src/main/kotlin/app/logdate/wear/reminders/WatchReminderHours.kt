package app.logdate.wear.reminders

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.domain.reminders.WakingHours
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.ReminderHours
import app.logdate.client.sync.peer.state.StateDocument
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

/** Retains waking hours only from the verified phone for offline reminder checks. */
class WatchReminderHours(
    private val storage: KeyValueStorage,
    private val acceptsPhone: suspend (String) -> Boolean,
) {
    private val mutex = Mutex()

    @Volatile var current: WakingHours = WakingHours.Fallback
        private set

    suspend fun initialize() =
        mutex.withLock {
            val saved = read()
            current = if (saved != null && acceptsPhone(saved.node)) saved.hours.asWakingHours() else WakingHours.Fallback
        }

    suspend fun adopt(
        node: String,
        document: StateDocument,
    ): Boolean =
        mutex.withLock {
            if (document.role != PeerRole.PHONE || !acceptsPhone(node)) return@withLock false
            val saved = read()
            if (saved?.node == node && saved.at >= document.writtenAt) return@withLock false
            val next = Saved(node, document.writtenAt, document.phone?.reminderHours)
            val encoded = WearJson.json.encodeToString(next)
            try {
                storage.putString(STORAGE_KEY, encoded)
                if (storage.getString(STORAGE_KEY) != encoded || !acceptsPhone(node)) return@withLock false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("Could not retain the phone's reminder waking hours", e)
                return@withLock false
            }
            val changed = current != next.hours.asWakingHours()
            current = next.hours.asWakingHours()
            changed
        }

    private suspend fun read(): Saved? {
        return try {
            val encoded = storage.getString(STORAGE_KEY) ?: return null
            WearJson.json.decodeFromString<Saved>(encoded)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Saved reminder waking hours could not be read", e)
            null
        }
    }

    private fun ReminderHours?.asWakingHours(): WakingHours =
        if (this?.valid == true) {
            WakingHours(LocalTime.fromSecondOfDay(wakeSecond), LocalTime.fromSecondOfDay(sleepSecond))
        } else {
            WakingHours.Fallback
        }

    @Serializable private data class Saved(
        val node: String,
        val at: Long,
        val hours: ReminderHours?,
    )

    companion object {
        const val STORAGE_KEY = "watch_reminder_hours_v1"
    }
}
