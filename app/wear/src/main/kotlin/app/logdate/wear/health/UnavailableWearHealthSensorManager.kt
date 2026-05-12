package app.logdate.wear.health

/**
 * No-op implementation of [WearHealthSensorManager] for devices without Health Services
 * or when health permissions are not granted.
 */
class UnavailableWearHealthSensorManager : WearHealthSensorManager {
    override suspend fun isAvailable(): Boolean = false

    override suspend fun sampleCurrent(): HealthSnapshot = HealthSnapshot()

    override suspend fun startPassiveMonitoring() { /* no-op */ }

    override suspend fun stopPassiveMonitoring() { /* no-op */ }
}
