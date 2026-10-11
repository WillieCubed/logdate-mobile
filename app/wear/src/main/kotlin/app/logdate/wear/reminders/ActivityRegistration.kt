package app.logdate.wear.reminders

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.clearPassiveListenerService
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.data.UserActivityState
import androidx.health.services.client.getCapabilities
import androidx.health.services.client.setPassiveListenerService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

/** Listening for the person's activity changing, which is how the watch sees a workout end. */
interface ActivityRegistration {
    /** Returns false when unsupported; transient provider failures propagate so restoration can retry. */
    suspend fun register(): Boolean

    suspend fun unregister()
}

/** [ActivityRegistration] through Health Services, delivering to [WorkoutEndListenerService]. */
class HealthServicesActivityRegistration(
    context: Context,
    clientProvider: () -> PassiveMonitoringClient = { HealthServices.getClient(context).passiveMonitoringClient },
) : ActivityRegistration {
    private val client by lazy(clientProvider)

    override suspend fun register(): Boolean {
        try {
            val required = setOf(UserActivityState.USER_ACTIVITY_EXERCISE, UserActivityState.USER_ACTIVITY_PASSIVE)
            if (!client.getCapabilities().supportedUserActivityStates.containsAll(required)) {
                Napier.i("This watch does not report workouts to apps, so reminders after workouts are off")
                return false
            }
            val config = PassiveListenerConfig.builder().setShouldUserActivityInfoBeRequested(true).build()
            client.setPassiveListenerService(WorkoutEndListenerService::class.java, config)
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Couldn't listen for workouts ending", e)
            throw e
        }
    }

    override suspend fun unregister() {
        try {
            client.clearPassiveListenerService()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Couldn't stop listening for workouts ending", e)
            throw e
        }
    }
}
