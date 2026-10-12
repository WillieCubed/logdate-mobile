package app.logdate.client.domain.reminders

import app.logdate.client.datastore.KeyValueStorage
import app.logdate.client.domain.recommendation.FakeKeyValueStorage
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReminderPostedHistoryTest {
    private val storage = FakeKeyValueStorage()
    private var now = 1_000L
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(now)
        }
    private val history = ReminderPostedHistory(storage, clock)

    @Test
    fun noPostStartsWithoutADailyLimit() =
        runTest {
            assertNull(history.latest())
        }

    @Test
    fun phonePostIsObservedAndSurvivesReopening() =
        runTest {
            val observed = mutableListOf<Instant?>()
            backgroundScope.launch { history.observe().collect { observed += it } }
            runCurrent()
            history.recordPostedAt(Instant.fromEpochMilliseconds(900))
            runCurrent()
            assertEquals(listOf(null, Instant.fromEpochMilliseconds(900)), observed)
            assertEquals(Instant.fromEpochMilliseconds(900), ReminderPostedHistory(storage, clock).latest())
        }

    @Test
    fun earlierPhonePostDoesNotMoveTheLimitBackwards() =
        runTest {
            history.recordPostedAt(Instant.fromEpochMilliseconds(900))
            history.recordPostedAt(Instant.fromEpochMilliseconds(800))
            assertEquals(Instant.fromEpochMilliseconds(900), history.latest())
        }

    @Test
    fun watchPostCountsTowardTheSameLimit() =
        runTest {
            history.recordPostedAt(Instant.fromEpochMilliseconds(800))
            history.adoptPeer("watch", 900)
            assertEquals(Instant.fromEpochMilliseconds(900), history.latest())
        }

    @Test
    fun futureWatchReplayKeepsItsFirstObservationAfterRestart() =
        runTest {
            history.adoptPeer("watch", 2_000)
            now = 1_500
            val reopened = ReminderPostedHistory(storage, clock)
            reopened.adoptPeer("watch", 2_000)
            assertEquals(Instant.fromEpochMilliseconds(1_000), reopened.latest())
        }

    @Test
    fun laterPostFromAnotherWatchStillAdvancesTheLimit() =
        runTest {
            history.adoptPeer("watch", 900)
            history.adoptPeer("second-watch", 950)
            history.adoptPeer("watch", 800)
            assertEquals(Instant.fromEpochMilliseconds(950), history.latest())
        }

    @Test
    fun overlappingPostsRetainTheLatestTime() =
        runTest {
            val yieldingStorage =
                object : KeyValueStorage by storage {
                    override suspend fun getString(key: String): String? {
                        val value = storage.getString(key)
                        yield()
                        return value
                    }
                }
            val concurrent = ReminderPostedHistory(yieldingStorage, clock)
            listOf(
                launch { concurrent.recordPostedAt(Instant.fromEpochMilliseconds(950)) },
                launch { concurrent.adoptPeer("watch", 900) },
            ).joinAll()
            assertEquals(Instant.fromEpochMilliseconds(950), concurrent.latest())
        }

    @Test
    fun corruptHistoryRefusesToResetTheDailyLimit() =
        runTest {
            for (raw in listOf("broken", "{\"schema\":2}", "{\"lastAt\":-1}")) {
                storage.putString(ReminderPostedHistory.STORAGE_KEY, raw)
                assertFailsWith<IllegalStateException> { history.latest() }
                assertFailsWith<IllegalStateException> { history.adoptPeer("watch", 900) }
                assertEquals(raw, storage.getString(ReminderPostedHistory.STORAGE_KEY))
            }
        }

    @Test
    fun reopenedPeerHistorySuppressesAnotherReminderOnTheSameStreakDay() =
        runTest {
            val instant = Instant.parse("2026-10-12T12:00:00Z")
            now = instant.toEpochMilliseconds()
            history.adoptPeer("watch", now)
            val reopened = ReminderPostedHistory(storage, clock)
            assertEquals(
                ReminderDecision.Skip(SkipReason.ALREADY_REMINDED_TODAY),
                ReminderOrchestrator.decide(
                    ReminderClock(instant, TimeZone.UTC, lastReminderAt = reopened.latest()),
                    ReminderSignals(enabled = true, writtenToday = false, workoutEndedAt = instant),
                ),
            )
        }

    @Test
    fun invalidPeerTimestampOrNodeCannotCreateHistory() =
        runTest {
            history.adoptPeer("watch", 0)
            history.adoptPeer("watch", -1)
            history.adoptPeer("", 900)
            assertNull(history.latest())
        }
}
