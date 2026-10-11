package app.logdate.wear.reminders

import app.logdate.wear.sync.PhoneReachability
import app.logdate.wear.sync.ReachablePhone
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EveningReminderCheckTest {
    private var active = true
    private var reads = 0
    private val notifier = FakeNudgeNotifier()
    private val dispatcher = WatchReminderDispatcher(FakeReminderPreferences(), notifier, FixedClock(berlin(10, 20, 30)), { REMINDER_ZONE })

    @Test
    fun `unreachable phone and empty day posts an evening reminder`() =
        runTest {
            assertTrue(check().run())
            assertEquals(listOf(WatchReminder.EVENING), notifier.posted)
        }

    @Test
    fun `reachable phone owns the evening without reading watch moments`() =
        runTest {
            assertFalse(check(phone = { flowOf(ReachablePhone("Phone")) }).run())
            assertEquals(0, reads)
        }

    @Test
    fun `populated day is quiet`() =
        runTest {
            assertFalse(check(count = { 1 }).run())
            assertTrue(notifier.posted.isEmpty())
        }

    @Test
    fun `disabled rollout or reminders do not query reachability`() =
        runTest {
            active = false
            assertFalse(check(phone = { error("Must not access phone") }).run())
            assertEquals(0, reads)
        }

    @Test
    fun `permission or preference revocation during a read suppresses the nudge`() =
        runTest {
            assertFalse(
                check(count = {
                    active = false
                    0
                }).run(),
            )
            assertTrue(notifier.posted.isEmpty())
        }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun `unknown reachability times out before the empty day check`() =
        runTest {
            assertTrue(check(phone = { flow { awaitCancellation() } }).run())
            assertEquals(5_000, testScheduler.currentTime)
        }

    @Test
    fun `read failure propagates for worker retry`() =
        runTest {
            assertFailsWith<IOException> { check(count = { throw IOException("unavailable") }).run() }
        }

    private fun check(
        phone: PhoneReachability = PhoneReachability { flowOf(null) },
        count: suspend () -> Int = { 0 },
    ) = EveningReminderCheck(
        active = { active },
        phoneReachability = phone,
        momentsToday = {
            reads++
            count()
        },
        dispatcher = dispatcher,
    )
}
