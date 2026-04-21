package app.logdate.client.domain.dayboundary

import app.logdate.client.domain.timeline.FakeHealthRepository
import app.logdate.client.health.HealthDataAvailability
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that [ObserveHealthConnectStatusUseCase] correctly transforms the raw health
 * repository availability into a structured [HealthConnectStatus] stream.
 *
 * Ensures that various Health Connect states (e.g., update required, not installed, connected)
 * are properly mapped and emitted to downstream consumers.
 */
class ObserveHealthConnectStatusUseCaseTest {
    private val repository = FakeHealthRepository()
    private val useCase = ObserveHealthConnectStatusUseCase(repository)

    @Test
    fun `emits provider update required when Health Connect setup is needed`() =
        runTest {
            repository.availability = HealthDataAvailability.PROVIDER_UPDATE_REQUIRED

            val statuses = useCase().toList()

            assertEquals(
                listOf(
                    HealthConnectStatus.CHECKING,
                    HealthConnectStatus.PROVIDER_UPDATE_REQUIRED,
                ),
                statuses,
            )
        }
}
