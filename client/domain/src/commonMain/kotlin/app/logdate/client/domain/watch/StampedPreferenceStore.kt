package app.logdate.client.domain.watch

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.sync.datalayer.v2.WearJson
import app.logdate.client.sync.peer.state.PeerRole
import app.logdate.client.sync.peer.state.Stamped
import app.logdate.client.sync.peer.state.StateMerger
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock

/**
 * This device's copy of the preferences it shares with its paired device, each with the time it was
 * written.
 *
 * A local write is stamped with the current time, and never earlier than the entry it replaces, so a
 * clock that moved backwards cannot make a new choice lose to an old one. [adopt] takes the other
 * device's entries through [StateMerger.mergeStamped], the rule both devices apply to their state
 * documents: the later write wins, a stamp from the future counts as now, and a tie goes to the phone.
 */
class StampedPreferenceStore(
    private val storage: KeyValueStorage,
    private val localRole: PeerRole,
    private val merger: StateMerger = StateMerger(),
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()

    fun observe(): Flow<Map<String, Stamped>> = storage.observeString(STORAGE_KEY).map(::decode).distinctUntilChanged()

    suspend fun snapshot(): Map<String, Stamped> = decode(storage.getString(STORAGE_KEY))

    /** Initializes an absent preference without replacing a choice made on either device. */
    suspend fun initializeMissing(
        name: String,
        value: JsonElement,
    ): Boolean =
        mutex.withLock {
            val text = storage.getString(STORAGE_KEY)
            val current =
                try {
                    if (text == null) emptyMap() else WearJson.json.decodeFromString(entriesSerializer, text)
                } catch (e: IllegalArgumentException) {
                    Napier.e("Cannot initialize unreadable shared preferences", e)
                    return@withLock false
                }
            if (name in current) return@withLock true
            // An imported legacy default must lose to a user choice delivered later by the peer.
            val stamped = Stamped(value, Long.MIN_VALUE)
            persist(current + (name to stamped))
            snapshot()[name] == stamped
        }

    suspend fun write(
        name: String,
        value: JsonElement,
    ): Stamped =
        mutex.withLock {
            val current = snapshot()
            val now = clock.now().toEpochMilliseconds()
            val at = current[name]?.let { maxOf(now, it.at + 1) } ?: now
            val stamped = Stamped(value = value, at = at)
            persist(current + (name to stamped))
            stamped
        }

    /** Takes each entry of [peer] that is later than this device's; true when anything changed. */
    suspend fun adopt(peer: Map<String, Stamped>): Boolean =
        mutex.withLock {
            if (peer.isEmpty()) return@withLock false
            val current = snapshot()
            val merged =
                when (localRole) {
                    PeerRole.PHONE -> merger.mergeStamped(phone = current, watch = peer)
                    PeerRole.WATCH -> merger.mergeStamped(phone = peer, watch = current)
                }
            if (merged == current) return@withLock false
            persist(merged)
            true
        }

    private suspend fun persist(entries: Map<String, Stamped>) {
        storage.putString(STORAGE_KEY, WearJson.json.encodeToString(entriesSerializer, entries))
    }

    private fun decode(text: String?): Map<String, Stamped> {
        if (text == null) return emptyMap()
        return try {
            WearJson.json.decodeFromString(entriesSerializer, text)
        } catch (e: IllegalArgumentException) {
            Napier.e("Stored watch preferences could not be read; each reads as its default until the next change", e)
            emptyMap()
        }
    }

    companion object {
        const val STORAGE_KEY = "watch_shared_preferences_v1"

        private val entriesSerializer = MapSerializer(String.serializer(), Stamped.serializer())
    }
}
