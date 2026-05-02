package app.logdate.client.health.datasource

import app.logdate.client.health.HealthDataAvailability
import app.logdate.client.health.IosSleepRepository
import app.logdate.client.health.model.SleepSession
import app.logdate.client.health.model.TimeOfDay
import kotlinx.datetime.TimeZone
import platform.HealthKit.HKHealthStore
import kotlin.time.Instant

/**
 * iOS [RemoteHealthDataSource] backed by HealthKit.
 *
 * Sleep-data queries delegate to [IosSleepRepository] so the actual `HKSampleQuery` plumbing lives
 * in one place. Like the rest of HealthKit on iOS, all calls require the HealthKit entitlement on
 * the iOS app target plus `NSHealthShareUsageDescription` in `Info.plist` — without those, methods
 * return graceful defaults rather than crashing.
 */
class IosHealthKitDataSource(
    private val healthStore: HKHealthStore = HKHealthStore(),
    private val sleepRepository: IosSleepRepository = IosSleepRepository(healthStore),
) : RemoteHealthDataSource {
    override suspend fun isAvailable(): Boolean = HKHealthStore.isHealthDataAvailable()

    override suspend fun getAvailability(): HealthDataAvailability =
        if (HKHealthStore.isHealthDataAvailable()) {
            HealthDataAvailability.AVAILABLE
        } else {
            HealthDataAvailability.NOT_AVAILABLE
        }

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
}
