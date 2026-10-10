package app.logdate.client.sync.peer.state

import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock

/** What both devices agree on, each field taken from its owner. */
data class MergedState(
    val phone: PhoneState?,
    val watch: WatchState?,
    val shared: Map<String, JsonElement>,
    val lastReminderAt: Long?,
)

/**
 * Combines the phone's and the watch's documents.
 *
 * A document only contributes the section its role owns. Shared entries go to the later write; a stamp
 * from the future counts as now, so a clock running ahead cannot win against a later write, and a tie
 * goes to the phone. The last reminder is the later of the two, so the one-a-day limit holds whichever
 * device delivered it. A watch's presence older than [PRESENCE_STALE_AFTER_MS] is unknown.
 */
class StateMerger(
    private val clock: Clock = Clock.System,
) {
    fun merge(
        phone: StateDocument?,
        watch: StateDocument?,
    ): MergedState {
        val now = clock.now().toEpochMilliseconds()
        val phoneDocument = phone?.takeIf { it.role == PeerRole.PHONE }
        val watchDocument = watch?.takeIf { it.role == PeerRole.WATCH }
        return MergedState(
            phone = phoneDocument?.phone,
            watch = watchDocument?.watch?.let { it.copy(presence = it.presence.asOf(now)) },
            shared = mergeStamped(phoneDocument?.shared.orEmpty(), watchDocument?.shared.orEmpty()).mapValues { it.value.value },
            lastReminderAt = listOfNotNull(phoneDocument?.lastReminderAt, watchDocument?.lastReminderAt).maxOrNull(),
        )
    }

    /**
     * The winning entry for every key either device wrote, with its stamp, so a device can store the
     * result and stamp its next write after it.
     */
    fun mergeStamped(
        phone: Map<String, Stamped>,
        watch: Map<String, Stamped>,
    ): Map<String, Stamped> {
        val now = clock.now().toEpochMilliseconds()
        return (phone.keys + watch.keys).associateWith { key -> later(phone[key], watch[key], now) }
    }

    private fun later(
        phone: Stamped?,
        watch: Stamped?,
        now: Long,
    ): Stamped {
        if (phone == null) return requireNotNull(watch)
        if (watch == null) return phone
        return if (minOf(watch.at, now) > minOf(phone.at, now)) watch else phone
    }

    private fun Presence.asOf(now: Long): Presence {
        if (now - minOf(observedAt, now) <= PRESENCE_STALE_AFTER_MS) return this
        return copy(state = PresenceState.UNKNOWN)
    }

    companion object {
        const val PRESENCE_STALE_AFTER_MS = 30 * 60 * 1_000L
    }
}
