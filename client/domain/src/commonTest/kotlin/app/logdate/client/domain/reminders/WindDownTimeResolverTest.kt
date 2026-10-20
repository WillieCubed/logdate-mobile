package app.logdate.client.domain.reminders

import app.logdate.client.health.SleepRepository
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class WindDownTimeResolverTest {
    @Test
    fun `known bedtime can match fallback hours without being a fallback`() =
        runTest {
            val sleep = Sessions(listOf(session("2026-10-11T22:30:00Z", "2026-10-12T07:30:00Z")))
            assertEquals(WakingHours(LocalTime(7, 30), LocalTime(22, 30)), WindDownTimeResolver(sleep).resolveKnown(now, zone))
            assertEquals(null, WindDownTimeResolver(Sessions(emptyList())).resolveKnown(now, zone))
        }

    private val now = Instant.parse("2026-10-12T20:00:00Z")
    private val zone = TimeZone.UTC

    @Test
    fun `completed sleep shifts the reminder to the learned wind down`() =
        runTest {
            val sleep = Sessions(listOf(session("2026-10-11T23:00:00Z", "2026-10-12T08:00:00Z")))
            val hours = WindDownTimeResolver(sleep).resolve(now, zone)
            assertEquals(WakingHours(LocalTime(8, 0), LocalTime(23, 0)), hours)
            assertEquals(
                ReminderDecision.Skip(SkipReason.NOTHING_TO_REMIND),
                ReminderOrchestrator.decide(ReminderClock(now, zone, hours), ReminderSignals(true, false)),
            )
            assertEquals(
                ReminderDecision.Deliver(ReminderTrigger.WIND_DOWN, 0),
                ReminderOrchestrator.decide(
                    ReminderClock(Instant.parse("2026-10-12T21:30:00Z"), zone, hours),
                    ReminderSignals(true, false),
                ),
            )
        }

    @Test
    fun `bedtimes across midnight average around midnight rather than noon`() =
        runTest {
            val sleep =
                Sessions(
                    listOf(
                        session("2026-10-10T23:30:00Z", "2026-10-11T07:30:00Z"),
                        session("2026-10-12T00:30:00Z", "2026-10-12T08:30:00Z"),
                    ),
                )
            assertEquals(WakingHours(LocalTime(8, 0), LocalTime(0, 0)), WindDownTimeResolver(sleep).resolve(now, zone))
        }

    @Test
    fun `session instants use the current zone`() =
        runTest {
            val sleep = Sessions(listOf(session("2026-10-11T06:00:00Z", "2026-10-11T15:00:00Z")))
            assertEquals(
                WakingHours(LocalTime(8, 0), LocalTime(23, 0)),
                WindDownTimeResolver(sleep).resolve(now, TimeZone.of("America/Los_Angeles")),
            )
        }

    @Test
    fun `unavailable sleep and revoked permission use fallback without requesting access`() =
        runTest {
            val sleep = Sessions(emptyList())
            val resolver = WindDownTimeResolver(sleep)
            assertEquals(WakingHours.Fallback, resolver.resolve(now, zone))
            sleep.sessions = listOf(session("2026-10-11T23:00:00Z", "2026-10-12T08:00:00Z"))
            sleep.granted = false
            assertEquals(WakingHours.Fallback, resolver.resolve(now, zone))
            sleep.granted = true
            sleep.afterRead = { sleep.granted = false }
            assertEquals(WakingHours.Fallback, resolver.resolve(now, zone))
        }

    @Test
    fun `unfinished future invalid and out of range sessions do not shift waking hours`() =
        runTest {
            val sleep =
                Sessions(
                    listOf(
                        session("2026-10-12T19:00:00Z", "2026-10-13T07:00:00Z"),
                        session("2026-10-12T08:00:00Z", "2026-10-12T07:00:00Z"),
                        session("2026-08-01T23:00:00Z", "2026-08-02T07:00:00Z"),
                        session("2026-10-13T23:00:00Z", "2026-10-14T07:00:00Z"),
                    ),
                )
            assertEquals(WakingHours.Fallback, WindDownTimeResolver(sleep).resolve(now, zone))
        }

    @Test
    fun `opposite bedtimes with no useful average use the fallback bedtime`() =
        runTest {
            val sleep =
                Sessions(
                    listOf(
                        session("2026-10-10T23:00:00Z", "2026-10-11T08:00:00Z"),
                        session("2026-10-11T11:00:00Z", "2026-10-12T08:00:00Z"),
                    ),
                )
            assertEquals(WakingHours(LocalTime(8, 0), LocalTime(22, 30)), WindDownTimeResolver(sleep).resolve(now, zone))
        }

    @Test
    fun `health read failures fall back while cancellation propagates`() =
        runTest {
            val sleep = Sessions(emptyList())
            sleep.failure = IllegalStateException("Health service unavailable")
            assertEquals(WakingHours.Fallback, WindDownTimeResolver(sleep).resolve(now, zone))
            sleep.failure = CancellationException("Stopped")
            assertFailsWith<CancellationException> { WindDownTimeResolver(sleep).resolve(now, zone) }
        }

    private fun session(
        start: String,
        end: String,
    ) = SleepSession(start, Instant.parse(start), Instant.parse(end))

    private class Sessions(
        var sessions: List<SleepSession>,
    ) : SleepRepository {
        var granted = true
        var failure: Exception? = null
        var afterRead: () -> Unit = {}

        override suspend fun hasSleepPermissions() = granted

        override suspend fun requestSleepPermissions(): Boolean = error("Must not request permission in background")

        override suspend fun getSleepSessions(
            start: Instant,
            end: Instant,
        ): List<SleepSession> {
            assertEquals(Instant.parse("2026-09-12T20:00:00Z"), start)
            assertEquals(Instant.parse("2026-10-12T20:00:00Z"), end)
            failure?.let { throw it }
            afterRead()
            return sessions
        }

        override suspend fun getAverageWakeUpTime(
            timeZone: TimeZone,
            days: Int,
        ): TimeOfDay? = error("Use a single session snapshot")

        override suspend fun getAverageSleepTime(
            timeZone: TimeZone,
            days: Int,
        ): TimeOfDay? = error("Use a single session snapshot")
    }
}
