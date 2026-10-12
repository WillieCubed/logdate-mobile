package app.logdate.wear.reminders

import app.logdate.client.sync.peer.state.Presence
import app.logdate.client.sync.peer.state.PresenceState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

fun interface PresenceRegistration {
    fun close()
}

/** Null registration means the watch has no usable off-body sensor. */
fun interface OffBodyReadings {
    fun subscribe(onReading: (Float?) -> Unit): PresenceRegistration?
}

class WearPresenceSampler(
    private val readings: OffBodyReadings,
    private val clock: Clock = Clock.System,
) {
    suspend fun sample(): Presence =
        try {
            withTimeoutOrNull(2_000) { firstReading() } ?: Presence()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Couldn't read whether the watch is worn", e)
            Presence()
        }

    private suspend fun firstReading(): Presence {
        val result = CompletableDeferred<Presence>()
        val registration = readings.subscribe { value -> result.complete(presence(value)) } ?: return Presence()
        return try {
            result.await()
        } finally {
            registration.close()
        }
    }

    private fun presence(value: Float?): Presence {
        val state =
            when (value) {
                1f -> PresenceState.ON_WRIST
                0f -> PresenceState.OFF_WRIST
                else -> return Presence()
            }
        return Presence(state, clock.now().toEpochMilliseconds())
    }
}
