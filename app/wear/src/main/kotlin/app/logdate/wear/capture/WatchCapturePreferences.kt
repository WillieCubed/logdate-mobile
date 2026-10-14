package app.logdate.wear.capture

import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.StampedPreferenceStore
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

/** Reads the durable shared choice at capture time, before permission or health/location reads. */
class WatchCapturePreferences(
    private val shared: StampedPreferenceStore,
    private val enabled: suspend () -> Boolean,
) {
    suspend fun capturePlaces(): Boolean? = if (enabled()) read(SharedPreferenceKey.CapturePlaces) else null

    suspend fun moodAfterRecording(): Boolean = if (enabled()) read(SharedPreferenceKey.MoodAfterRecording) else true

    private suspend fun read(key: SharedPreferenceKey<Boolean>): Boolean =
        try {
            key.read(shared.snapshot()[key.name])
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not read shared capture choice; skipping optional capture", e)
            false
        }
}
