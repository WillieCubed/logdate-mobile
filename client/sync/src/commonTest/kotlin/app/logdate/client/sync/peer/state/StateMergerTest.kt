package app.logdate.client.sync.peer.state

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Tests [StateMerger]: every field has one owner, shared entries go to the later write with ties to
 * the phone, and a watch's presence goes stale after half an hour.
 */
class StateMergerTest {
    private val merger = StateMerger(FixedClock(NOW))

    @Test
    fun `phone owned fields come only from the phone document`() {
        val phone = phoneDocument(PhoneState(flags = mapOf("flag" to true)))
        val watch = watchDocument().copy(phone = PhoneState(flags = mapOf("flag" to false)))

        assertEquals(PhoneState(flags = mapOf("flag" to true)), merger.merge(phone, watch).phone)
    }

    @Test
    fun `watch owned fields come only from the watch document`() {
        val phone = phoneDocument(PhoneState()).copy(watch = WatchState(outboxCount = 99))

        assertNull(merger.merge(phone, watch = null).watch)
        assertEquals(4, merger.merge(phone, watchDocument(WatchState(outboxCount = 4))).watch?.outboxCount)
    }

    @Test
    fun `a document passed as the wrong role contributes nothing`() {
        val merged = merger.merge(phone = watchDocument(shared = mapOf("pref" to stamp("a", NOW))), watch = phoneDocument(PhoneState()))

        assertNull(merged.phone)
        assertNull(merged.watch)
        assertEquals(emptyMap(), merged.shared)
    }

    @Test
    fun `the later shared write wins`() {
        val merged =
            merger.merge(
                phoneDocument(PhoneState(), shared = mapOf("pref" to stamp("phone", NOW - 10))),
                watchDocument(shared = mapOf("pref" to stamp("watch", NOW - 5))),
            )

        assertEquals(JsonPrimitive("watch"), merged.shared["pref"])
    }

    @Test
    fun `a shared tie goes to the phone`() {
        val merged =
            merger.merge(
                phoneDocument(PhoneState(), shared = mapOf("pref" to stamp("phone", NOW - 5))),
                watchDocument(shared = mapOf("pref" to stamp("watch", NOW - 5))),
            )

        assertEquals(JsonPrimitive("phone"), merged.shared["pref"])
    }

    @Test
    fun `an entry only one device wrote is used`() {
        val merged = merger.merge(phoneDocument(PhoneState()), watchDocument(shared = mapOf("pref" to stamp("watch", NOW))))

        assertEquals(JsonPrimitive("watch"), merged.shared["pref"])
    }

    @Test
    fun `a clock running ahead cannot outlast a later write`() {
        val merged =
            merger.merge(
                phoneDocument(PhoneState(), shared = mapOf("pref" to stamp("phone", NOW))),
                watchDocument(shared = mapOf("pref" to stamp("watch", NOW + 3_600_000))),
            )

        assertEquals(JsonPrimitive("phone"), merged.shared["pref"])
    }

    @Test
    fun `a clock running ahead still beats an older write`() {
        val merged =
            merger.merge(
                phoneDocument(PhoneState(), shared = mapOf("pref" to stamp("phone", NOW - 60_000))),
                watchDocument(shared = mapOf("pref" to stamp("watch", NOW + 3_600_000))),
            )

        assertEquals(JsonPrimitive("watch"), merged.shared["pref"])
    }

    @Test
    fun `the last reminder is the latest either device recorded`() {
        assertEquals(
            900L,
            merger.merge(phoneDocument(PhoneState()).copy(lastReminderAt = 900), watchDocument().copy(lastReminderAt = 800)).lastReminderAt,
        )
        assertEquals(800L, merger.merge(phoneDocument(PhoneState()), watchDocument().copy(lastReminderAt = 800)).lastReminderAt)
        assertNull(merger.merge(phoneDocument(PhoneState()), watchDocument()).lastReminderAt)
    }

    @Test
    fun `presence older than 30 minutes is unknown`() {
        val fresh = watchDocument(WatchState(presence = Presence(PresenceState.ON_WRIST, observedAt = NOW - THIRTY_MINUTES)))
        val stale = watchDocument(WatchState(presence = Presence(PresenceState.ON_WRIST, observedAt = NOW - THIRTY_MINUTES - 1)))

        assertEquals(
            PresenceState.ON_WRIST,
            merger
                .merge(phone = null, watch = fresh)
                .watch
                ?.presence
                ?.state,
        )
        assertEquals(
            PresenceState.UNKNOWN,
            merger
                .merge(phone = null, watch = stale)
                .watch
                ?.presence
                ?.state,
        )
    }

    @Test
    fun `presence stamped in the future counts as fresh`() {
        val ahead = watchDocument(WatchState(presence = Presence(PresenceState.OFF_WRIST, observedAt = NOW + 3_600_000)))

        assertEquals(
            PresenceState.OFF_WRIST,
            merger
                .merge(phone = null, watch = ahead)
                .watch
                ?.presence
                ?.state,
        )
    }

    @Test
    fun `merging stamped entries keeps the winning stamp`() {
        val merged =
            merger.mergeStamped(
                phone = mapOf("a" to stamp("phone", NOW - 10), "b" to stamp("phone", NOW - 1)),
                watch = mapOf("a" to stamp("watch", NOW - 5), "c" to stamp("watch", NOW - 2)),
            )

        assertEquals(
            mapOf("a" to stamp("watch", NOW - 5), "b" to stamp("phone", NOW - 1), "c" to stamp("watch", NOW - 2)),
            merged,
        )
    }

    @Test
    fun `merging stamped entries gives a tie to the phone`() {
        val merged = merger.mergeStamped(phone = mapOf("a" to stamp("phone", NOW - 5)), watch = mapOf("a" to stamp("watch", NOW - 5)))

        assertEquals(stamp("phone", NOW - 5), merged["a"])
    }

    @Test
    fun `a stamped entry from the future cannot beat a current one`() {
        val merged = merger.mergeStamped(phone = mapOf("a" to stamp("phone", NOW)), watch = mapOf("a" to stamp("watch", NOW + 60_000)))

        assertEquals(stamp("phone", NOW), merged["a"])
    }

    private fun phoneDocument(
        phone: PhoneState,
        shared: Map<String, Stamped> = emptyMap(),
    ) = StateDocument(role = PeerRole.PHONE, writtenAt = NOW, phone = phone, shared = shared)

    private fun watchDocument(
        watch: WatchState = WatchState(),
        shared: Map<String, Stamped> = emptyMap(),
    ) = StateDocument(role = PeerRole.WATCH, writtenAt = NOW, watch = watch, shared = shared)

    private fun stamp(
        value: String,
        at: Long,
    ) = Stamped(JsonPrimitive(value), at)

    private class FixedClock(
        private val millis: Long,
    ) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }

    private companion object {
        const val NOW = 1_710_000_000_000
        const val THIRTY_MINUTES = 30 * 60 * 1_000L
    }
}
