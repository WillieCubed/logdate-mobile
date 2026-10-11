package app.logdate.wear.reminders

import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.PassiveMonitoringCapabilities
import androidx.health.services.client.data.UserActivityState
import com.google.common.util.concurrent.Futures
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HealthServicesActivityRegistrationTest {
    private val client = mockk<PassiveMonitoringClient>()
    private val registration = HealthServicesActivityRegistration(mockk(), clientProvider = { client })

    @Test
    fun `workout detection requires exercise and passive states`() =
        runTest {
            capabilities(setOf(UserActivityState.USER_ACTIVITY_EXERCISE))
            every { client.setPassiveListenerServiceAsync(any(), any()) } returns Futures.immediateFuture(null)
            assertFalse(registration.register())
            verify(exactly = 0) { client.setPassiveListenerServiceAsync(any(), any()) }
        }

    @Test
    fun `supported watches register the protected workout listener`() =
        runTest {
            capabilities(setOf(UserActivityState.USER_ACTIVITY_EXERCISE, UserActivityState.USER_ACTIVITY_PASSIVE))
            every { client.setPassiveListenerServiceAsync(any(), any()) } returns Futures.immediateFuture(null)
            assertTrue(registration.register())
            verify { client.setPassiveListenerServiceAsync(WorkoutEndListenerService::class.java, any()) }
        }

    @Test
    fun `a transient capability failure reaches the worker retry boundary`() =
        runTest {
            every { client.getCapabilitiesAsync() } returns Futures.immediateFailedFuture(IllegalStateException("Health Services starting"))
            assertFailsWith<IllegalStateException> { registration.register() }
        }

    @Test
    fun `a failed unregister reaches the worker retry boundary`() =
        runTest {
            every { client.clearPassiveListenerServiceAsync() } returns
                Futures.immediateFailedFuture(IllegalStateException("Health Services starting"))
            assertFailsWith<IllegalStateException> { registration.unregister() }
        }

    private fun capabilities(states: Set<UserActivityState>) {
        val capabilities =
            mockk<PassiveMonitoringCapabilities> {
                every { supportedUserActivityStates } returns states
            }
        every { client.getCapabilitiesAsync() } returns Futures.immediateFuture(capabilities)
    }
}
