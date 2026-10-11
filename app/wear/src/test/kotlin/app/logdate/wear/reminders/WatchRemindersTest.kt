package app.logdate.wear.reminders

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchRemindersTest {
    private val preferences = FakeReminderPreferences(enabled = false)
    private val activity = FakeActivityRegistration()
    private val permissions = FakeReminderPermissions()
    private val reminders = WatchReminders(preferences = preferences, activity = activity, permissions = permissions)

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
            val controller = WatchReminders(preferences, slowRegistration, permissions)
            val enable = launch { controller.setEnabled(true) }
            entered.await()
            val disable = launch { controller.setEnabled(false) }
            runCurrent()
            finish.complete(Unit)
            joinAll(enable, disable)
            assertFalse(listening)
            assertFalse(preferences.enabled.value)
        }

    @Test
    fun `the rollout gate stops a restored workout listener`() =
        runTest {
            preferences.setEnabled(true)
            val controller = WatchReminders(preferences, activity, permissions, experienceEnabled = { false })
            controller.restore()
            assertEquals(0, activity.registrations)
            assertEquals(1, activity.unregistrations)
            assertTrue(preferences.enabled.value)
        }

    @Test
    fun `notification access is required before collecting workout activity`() =
        runTest {
            permissions.notifications = false
            reminders.setEnabled(true)
            assertEquals(0, activity.registrations)
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
    fun `without physical activity access nothing listens for workouts`() =
        runTest {
            permissions.activityRecognition = false

            reminders.setEnabled(true)

            assertEquals(0, activity.registrations)
        }

    @Test
    fun `a restart listens again only while reminders are on`() =
        runTest {
            reminders.restore()
            assertEquals(0, activity.registrations)
            preferences.setEnabled(true)

            reminders.restore()

            assertEquals(1, activity.registrations)
        }
}
