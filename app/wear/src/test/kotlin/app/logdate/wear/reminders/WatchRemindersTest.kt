package app.logdate.wear.reminders

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchRemindersTest {
    @Test
    fun `watch switch persists shared choice before changing local registration`() =
        runTest {
            var sharedChoice: Boolean? = null
            val reminders =
                WatchReminders(
                    preferences,
                    activity,
                    permissions,
                    evening,
                    writeSharedChoice = { enabled ->
                        assertFalse(preferences.enabled.value)
                        sharedChoice = enabled
                    },
                )
            reminders.setEnabled(true)
            assertEquals(true, sharedChoice)
            assertTrue(preferences.enabled.value)
        }

    private val preferences = FakeReminderPreferences(enabled = false)
    private val activity = FakeActivityRegistration()
    private val permissions = FakeReminderPermissions()
    private val eveningWork = FakeEveningWork()
    private val evening = EveningReminderScheduler(eveningWork, FixedClock(berlin(10, 18)), timeZone = { REMINDER_ZONE })
    private val reminders = WatchReminders(preferences = preferences, activity = activity, permissions = permissions, evening = evening)

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `cold startup waits before canceling tonight or skipping the due worker`() =
        runTest {
            preferences.setEnabled(true)
            var resolvedFlag = false
            val read = CompletableDeferred<Unit>()
            val controller =
                WatchReminders(
                    preferences,
                    activity,
                    permissions,
                    evening,
                    experienceEnabled = { resolvedFlag },
                    awaitExperience = { read.await() },
                )
            val restoring = launch { controller.restore() }
            val workerActive = async { controller.awaitActive() }
            runCurrent()
            assertEquals(0, eveningWork.cancels)
            assertFalse(workerActive.isCompleted)
            resolvedFlag = true
            read.complete(Unit)
            restoring.join()
            assertTrue(workerActive.await())
            assertEquals(0, eveningWork.cancels)
            assertEquals(1, eveningWork.ensures)
        }

    @Test
    fun `restoration preserves waiting or running evening work`() =
        runTest {
            preferences.setEnabled(true)
            reminders.restore()
            reminders.restore()
            assertEquals(2, eveningWork.ensures)
            assertEquals(0, eveningWork.replacements)
        }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `turning off during registration leaves the listener stopped`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            var listening = false
            val slowRegistration =
                object : ActivityRegistration {
                    override suspend fun register(): Boolean {
                        entered.complete(Unit)
                        finish.await()
                        listening = true
                        return true
                    }

                    override suspend fun unregister() {
                        listening = false
                    }
                }
            val controller = WatchReminders(preferences, slowRegistration, permissions, evening)
            val enable = launch { controller.setEnabled(true) }
            entered.await()
            val disable = launch { controller.setEnabled(false) }
            runCurrent()
            finish.complete(Unit)
            joinAll(enable, disable)
            assertFalse(listening)
            assertFalse(preferences.enabled.value)
            assertTrue(eveningWork.cancels > 0)
        }

    @Test
    fun `the rollout gate stops a restored workout listener`() =
        runTest {
            preferences.setEnabled(true)
            val controller = WatchReminders(preferences, activity, permissions, evening, experienceEnabled = { false })
            controller.restore()
            assertEquals(0, activity.registrations)
            assertEquals(1, activity.unregistrations)
            assertTrue(eveningWork.delays.isEmpty())
            assertTrue(preferences.enabled.value)
        }

    @Test
    fun `notification access is required before collecting workout activity`() =
        runTest {
            permissions.notifications = false
            reminders.setEnabled(true)
            assertEquals(0, activity.registrations)
            assertEquals(if (permissions.notifications) 1 else 0, eveningWork.delays.size)
        }

    @Test
    fun `successful registration reports active workouts and disabling clears it`() =
        runTest {
            reminders.setEnabled(true)
            assertTrue(reminders.workoutsActive.value)
            reminders.setEnabled(false)
            assertFalse(reminders.workoutsActive.value)
        }

    @Test
    fun `turning reminders on listens for workouts`() =
        runTest {
            reminders.setEnabled(true)

            assertTrue(preferences.enabled.value)
            assertEquals(1, activity.registrations)
            assertEquals(1, eveningWork.delays.size)
        }

    @Test
    fun `turning reminders off stops listening`() =
        runTest {
            reminders.setEnabled(true)

            reminders.setEnabled(false)

            assertFalse(preferences.enabled.value)
            assertEquals(1, activity.unregistrations)
        }

    @Test
    fun `without physical activity access the evening still comes`() =
        runTest {
            permissions.activityRecognition = false

            reminders.setEnabled(true)

            assertEquals(0, activity.registrations)
            assertEquals(if (permissions.notifications) 1 else 0, eveningWork.delays.size)
        }

    @Test
    fun `a restart listens again only while reminders are on`() =
        runTest {
            reminders.restore()
            assertEquals(0, activity.registrations)
            preferences.setEnabled(true)

            reminders.restore()

            assertEquals(1, activity.registrations)
            assertEquals(1, eveningWork.delays.size)
        }
}
