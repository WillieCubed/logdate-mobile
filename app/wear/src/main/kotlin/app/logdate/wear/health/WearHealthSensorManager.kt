package app.logdate.wear.health

/**
 * Snapshot of health data at a moment in time, used to annotate journal entries.
 */
data class HealthSnapshot(
    val heartRateBpm: Int? = null,
    val stepCount: Int? = null,
    val stressLevel: Float? = null,
)

/**
 * Provides access to Wear OS health sensor data for annotating journal entries.
 * Wraps Health Services PassiveMonitoringClient for heart rate and step count.
 */
interface WearHealthSensorManager {
    /**
     * Whether Health Services is available on this device.
     */
    suspend fun isAvailable(): Boolean

    /**
     * Sample current health data. Returns whatever is available from passive monitoring.
     * Fields may be null if the sensor is not active or data is unavailable.
     */
    suspend fun sampleCurrent(): HealthSnapshot

    /**
     * Start passive monitoring for health data. Call once at app startup.
     * No-op if already started or if Health Services is unavailable.
     */
    suspend fun startPassiveMonitoring()

    /**
     * Stop passive monitoring. Call during cleanup.
     */
    suspend fun stopPassiveMonitoring()
}
