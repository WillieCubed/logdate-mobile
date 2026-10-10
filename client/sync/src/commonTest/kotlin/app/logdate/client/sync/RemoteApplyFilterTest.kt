package app.logdate.client.sync

import app.logdate.client.repository.journals.JournalNote
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Tests the filters that decide which notes only the server has are stored on a device.
 */
class RemoteApplyFilterTest {
    @Test
    fun `every note applies by default`() {
        assertTrue(RemoteApplyFilter.ALL.appliesNote(note(Instant.fromEpochMilliseconds(0))))
    }

    @Test
    fun `a recent capture filter keeps notes inside its window`() {
        val filter = RecentCaptureFilter(window = 14.days, clock = FixedClock(NOW))

        assertTrue(filter.appliesNote(note(NOW)))
        assertTrue(filter.appliesNote(note(NOW - 14.days)))
        assertFalse(filter.appliesNote(note(NOW - 14.days - 1.milliseconds)))
    }

    private fun note(at: Instant) = JournalNote.Text(uid = Uuid.random(), creationTimestamp = at, lastUpdated = at, content = "x")

    private class FixedClock(
        private val now: Instant,
    ) : Clock {
        override fun now(): Instant = now
    }

    private companion object {
        val NOW = Instant.fromEpochMilliseconds(1_710_000_000_000)
    }
}
