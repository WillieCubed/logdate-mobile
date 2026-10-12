package app.logdate.client.domain.reminders

import app.logdate.client.health.SleepRepository
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LearnedReminderHoursTest {
    private val enabled = MutableStateFlow(false)
    private var reads = 0
    private var granted = false
    private var gate: CompletableDeferred<Unit>? = null
    private val sleep =
        object : SleepRepository {
            override suspend fun hasSleepPermissions(): Boolean {
                reads++
                return granted
            }

            override suspend fun requestSleepPermissions(): Boolean = error("No permission requests in background")

            override suspend fun getSleepSessions(
                start: Instant,
                end: Instant,
            ): List<SleepSession> {
                gate?.await()
                return listOf(SleepSession("sleep", Instant.parse("2026-10-11T23:00:00Z"), Instant.parse("2026-10-12T08:00:00Z")))
            }

            override suspend fun getAverageWakeUpTime(
                timeZone: TimeZone,
                days: Int,
            ): TimeOfDay? = error("No access")

            override suspend fun getAverageSleepTime(
                timeZone: TimeZone,
                days: Int,
            ): TimeOfDay? = error("No access")
        }
    private val source =
        LearnedReminderHours(
            enabled,
            WindDownTimeResolver(sleep),
            clock =
                object : Clock {
                    override fun now() = Instant.parse("2026-10-12T20:00:00Z")
                },
            zone = { TimeZone.UTC },
        )

    @Test fun `actual sleep sessions supply learned hours`() =
        runTest {
            enabled.value = true
            granted = true
            assertEquals(WakingHours(LocalTime(8, 0), LocalTime(23, 0)), source.observe().first())
        }

    @Test fun `opt out cancels suspended sleep reads and withdraws learned hours`() =
        runTest {
            enabled.value = true
            granted = true
            gate = CompletableDeferred()
            val values = mutableListOf<WakingHours?>()
            backgroundScope.launch { source.observe().collect { values += it } }
            runCurrent()
            enabled.value = false
            runCurrent()
            gate?.complete(Unit)
            runCurrent()
            assertEquals(listOf<WakingHours?>(null), values)
        }

    @Test
    fun `no private sleep reads while reminders are off`() =
        runTest {
            assertEquals(null, source.observe().first())
            assertEquals(0, reads)
        }

    @Test
    fun `enabling learns hours and disabling withdraws them`() =
        runTest {
            val values = mutableListOf<WakingHours?>()
            backgroundScope.launch { source.observe().collect { values += it } }
            runCurrent()
            enabled.value = true
            runCurrent()
            assertEquals(listOf(null, WakingHours.Fallback), values)
            enabled.value = false
            runCurrent()
            assertEquals(listOf(null, WakingHours.Fallback, null), values)
        }

    @Test
    fun `scheduled refresh reads current health access again`() =
        runTest {
            enabled.value = true
            backgroundScope.launch { source.observe().collect {} }
            runCurrent()
            assertEquals(1, reads)
            source.refresh()
            runCurrent()
            assertEquals(2, reads)
        }
}
