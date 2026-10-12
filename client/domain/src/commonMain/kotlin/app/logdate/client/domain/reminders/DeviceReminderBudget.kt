package app.logdate.client.domain.reminders

import app.logdate.client.datastore.KeyValueStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate

/** Conservative device-wide daily limit, shared by standalone and paired delivery. */
class DeviceReminderBudget(
    private val storage: KeyValueStorage,
) {
    private val mutex = Mutex()

    suspend fun available(day: LocalDate): Boolean = mutex.withLock { lastDay()?.let { it < day } ?: true }

    suspend fun consume(day: LocalDate): Boolean =
        mutex.withLock {
            if (lastDay()?.let { it >= day } == true) return@withLock false
            storage.putString(STORAGE_KEY, day.toString())
            true
        }

    private suspend fun lastDay(): LocalDate? {
        val text = storage.getString(STORAGE_KEY) ?: return null
        return try {
            LocalDate.parse(text)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("The device's reminder budget could not be read", e)
        }
    }

    companion object {
        const val STORAGE_KEY = "moment_reminder_device_budget_v1"
    }
}
