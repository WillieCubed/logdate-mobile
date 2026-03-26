package app.logdate.client.domain.dayboundary

import app.logdate.client.health.LocalFirstHealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Health Connect connection status from the user's perspective.
 */
enum class HealthConnectStatus {
    /** Health Connect is available and sleep permissions are granted. */
    CONNECTED,

    /** Health Connect is available but sleep permissions have not been granted. */
    PERMISSIONS_NEEDED,

    /** Health Connect is not available on this device. */
    NOT_AVAILABLE,

    /** Status is still being determined. */
    CHECKING,
}

/**
 * Checks whether Health Connect is available and sleep permissions are granted.
 */
class ObserveHealthConnectStatusUseCase(
    private val healthRepository: LocalFirstHealthRepository,
) {
    operator fun invoke(): Flow<HealthConnectStatus> =
        flow {
            emit(HealthConnectStatus.CHECKING)

            val available = healthRepository.isHealthDataAvailable()
            if (!available) {
                emit(HealthConnectStatus.NOT_AVAILABLE)
                return@flow
            }

            val hasPermissions = healthRepository.hasSleepPermissions()
            if (hasPermissions) {
                emit(HealthConnectStatus.CONNECTED)
            } else {
                emit(HealthConnectStatus.PERMISSIONS_NEEDED)
            }
        }
}
