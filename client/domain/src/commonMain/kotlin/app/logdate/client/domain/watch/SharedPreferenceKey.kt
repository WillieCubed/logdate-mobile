package app.logdate.client.domain.watch

import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.state.Stamped
import io.github.aakira.napier.Napier
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

/**
 * A preference the phone and the watch share through the `shared` section of their state documents.
 *
 * [name] is the key on the wire and never changes once shipped. A stored value that cannot be read as
 * [T], or that [isAllowed] rejects (for example one a newer app version wrote), reads as [default]
 * rather than failing.
 */
class SharedPreferenceKey<T>(
    val name: String,
    val default: T,
    private val serializer: KSerializer<T>,
    private val allowed: (T) -> Boolean = { true },
) {
    fun isAllowed(value: T): Boolean = allowed(value)

    fun encode(value: T): JsonElement = WearJson.json.encodeToJsonElement(serializer, value)

    fun read(stamped: Stamped?): T {
        if (stamped == null) return default
        val value =
            try {
                WearJson.json.decodeFromJsonElement(serializer, stamped.value)
            } catch (e: IllegalArgumentException) {
                return unreadable(e)
            } catch (e: IllegalStateException) {
                return unreadable(e)
            }
        if (allowed(value)) return value
        Napier.w("The shared preference $name has a value outside what this version allows; it reads as its default")
        return default
    }

    private fun unreadable(error: Exception): T {
        Napier.w("The shared preference $name has a value this version cannot read; it reads as its default", error)
        return default
    }

    override fun toString(): String = "SharedPreferenceKey($name)"

    companion object {
        /** Moment-aware reminders, off until someone turns them on. */
        val RemindersEnabled = SharedPreferenceKey("reminders.enabled", false, Boolean.serializer())

        /** Whether marks and watch captures get a place. Off until someone agrees to it on either device. */
        val CapturePlaces = SharedPreferenceKey("capture.places", false, Boolean.serializer())

        /** Whether the watch offers the five feelings right after a recording is saved. */
        val MoodAfterRecording = SharedPreferenceKey("capture.moodAfterRecording", true, Boolean.serializer())

        /** How many days of moments the watch library covers: one of [WINDOW_DAY_CHOICES]. */
        val WindowDays = SharedPreferenceKey("window.days", 14, Int.serializer()) { it in WINDOW_DAY_CHOICES }

        /** Whether the watch library includes On this day. */
        val WindowOnThisDay = SharedPreferenceKey("window.onThisDay", true, Boolean.serializer())

        /** Whether the watch library includes this week's Rewind. */
        val WindowRewind = SharedPreferenceKey("window.rewind", true, Boolean.serializer())

        val AudioDownloadPolicy =
            SharedPreferenceKey(
                "window.audioDownloads",
                WatchAudioDownloadPolicy.WIFI_OR_CHARGING.stored,
                String.serializer(),
            ) { value -> WatchAudioDownloadPolicy.entries.any { it.stored == value } }

        val WINDOW_DAY_CHOICES: Set<Int> = setOf(7, 14, 30)

        val ALL: List<SharedPreferenceKey<*>> =
            listOf(
                RemindersEnabled,
                CapturePlaces,
                MoodAfterRecording,
                WindowDays,
                WindowOnThisDay,
                WindowRewind,
                AudioDownloadPolicy,
            )
    }
}
