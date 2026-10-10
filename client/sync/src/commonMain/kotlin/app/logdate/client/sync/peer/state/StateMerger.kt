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
            shared = mergeShared(phoneDocument?.shared.orEmpty(), watchDocument?.shared.orEmpty(), now),
            lastReminderAt = listOfNotNull(phoneDocument?.lastReminderAt, watchDocument?.lastReminderAt).maxOrNull(),
        )
    }

    private fun mergeShared(
        phone: Map<String, Stamped>,
        watch: Map<String, Stamped>,
        now: Long,
    ): Map<String, JsonElement> = (phone.keys + watch.keys).associateWith { key -> later(phone[key], watch[key], now).value }

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
