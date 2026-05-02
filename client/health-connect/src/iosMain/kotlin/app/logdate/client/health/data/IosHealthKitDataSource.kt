package app.logdate.client.health.data

import app.logdate.client.health.IosSleepRepository
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import kotlinx.datetime.TimeZone
import platform.HealthKit.HKCategoryTypeIdentifierSleepAnalysis
import platform.HealthKit.HKHealthStore
import kotlin.time.Instant

/**
 * iOS [RemoteHealthDataSource] backed by HealthKit. Wraps an [IosSleepRepository] for the
 * sleep-related methods so the underlying HealthKit query logic lives in one place.
 *
 * Requires the HealthKit entitlement on the iOS app target plus
 * `NSHealthShareUsageDescription` in Info.plist; without those, all calls return graceful
 * defaults (false / empty / null) rather than crashing.
 */
class IosHealthKitDataSource(
    private val healthStore: HKHealthStore = HKHealthStore(),
    private val sleepRepository: IosSleepRepository = IosSleepRepository(healthStore),
) : RemoteHealthDataSource {
    override suspend fun isHealthApiAvailable(): Boolean = HKHealthStore.isHealthDataAvailable()

    override suspend fun hasSleepPermissions(): Boolean = sleepRepository.hasSleepPermissions()

    override suspend fun requestSleepPermissions(): Boolean = sleepRepository.requestSleepPermissions()

    override suspend fun getSleepSessions(
        start: Instant,
        end: Instant,
    ): List<SleepSession> = sleepRepository.getSleepSessions(start, end)

    override suspend fun getAverageWakeUpTime(
        timeZone: TimeZone,
        days: Int,
    ): TimeOfDay? = sleepRepository.getAverageWakeUpTime(timeZone, days)

    override suspend fun getAverageSleepTime(
        timeZone: TimeZone,
        days: Int,
    ): TimeOfDay? = sleepRepository.getAverageSleepTime(timeZone, days)

    override suspend fun getAvailableDataTypes(): List<String> =
        if (HKHealthStore.isHealthDataAvailable()) {
            listOfNotNull(HKCategoryTypeIdentifierSleepAnalysis)
        } else {
            emptyList()
        }
}
