package app.logdate.client.location.history

import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.shared.model.Location
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

/**
 * Standard implementation of LocationTracker that combines a location provider
 * with a location history repository to track and manage location data.
 */
class StandardLocationTracker(
    private val locationProvider: ClientLocationProvider,
    private val locationHistoryRepository: LocationHistoryRepository,
    private val deviceId: String,
    private val userId: String = "default_user" // Should be injected from auth system
) : LocationTracker {

    override suspend fun getLastLocation(): LocationHistoryItem? {
        return locationHistoryRepository.getLastLocation()
    }
    
    override fun observeCurrentLocation(): Flow<LocationHistoryItem?> {
        return locationHistoryRepository.observeLastLocation()
            .catch { e -> 
                Napier.e("Error observing location", e)
                emit(null)
            }
    }
    
    override suspend fun getLocationHistoryForDate(date: LocalDate): List<LocationHistoryItem> {
        val startTime = date.atStartOfDayIn(TimeZone.currentSystemDefault())
        val endTime = date.plus(kotlinx.datetime.DatePeriod(days = 1))
            .atStartOfDayIn(TimeZone.currentSystemDefault())
        
        return locationHistoryRepository.getLocationHistoryBetween(startTime, endTime)
    }
    
    override suspend fun getLocationHistoryBetween(start: Instant, end: Instant): List<LocationHistoryItem> {
        return locationHistoryRepository.getLocationHistoryBetween(start, end)
    }
    
    override suspend fun logCurrentLocation(): Result<LocationHistoryItem> {
        return try {
            val location = locationProvider.getCurrentLocation()
            logLocation(location)
        } catch (e: Exception) {
            Napier.e("Failed to log current location", e)
            Result.failure(e)
        }
    }
    
    override suspend fun logLocation(
        location: Location,
        timestamp: Instant,
        metadata: Map<String, Any>
    ): Result<LocationHistoryItem> {
        return try {
            val confidence = metadata["confidence"] as? Float ?: 1.0f
            val isGenuine = metadata["isGenuine"] as? Boolean ?: true
            
            val result = locationHistoryRepository.logLocation(
                location = location,
                userId = userId,
                deviceId = deviceId,
                confidence = confidence,
                isGenuine = isGenuine
            )
            
            if (result.isSuccess) {
                val historyItem = LocationHistoryItem(
                    userId = userId,
                    deviceId = deviceId,
                    timestamp = timestamp,
                    location = location,
                    confidence = confidence,
                    isGenuine = isGenuine
                )
                Result.success(historyItem)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to log location"))
            }
        } catch (e: Exception) {
            Napier.e("Failed to log location", e)
            Result.failure(e)
        }
    }
    
    override suspend fun deleteLocationEntry(historyItem: LocationHistoryItem): Result<Unit> {
        return locationHistoryRepository.deleteLocationEntry(
            userId = historyItem.userId,
            deviceId = historyItem.deviceId,
            timestamp = historyItem.timestamp
        )
    }
    
    override suspend fun clearLocationHistory(start: Instant, end: Instant): Result<Unit> {
        return locationHistoryRepository.deleteLocationsBetween(start, end)
    }
    
    override suspend fun clearAllLocationHistory(): Result<Unit> {
        return try {
            // Since the repository doesn't have a direct method for this,
            // we use a very wide time range
            val startTime = Instant.fromEpochMilliseconds(0) // Beginning of time
            val endTime = Instant.fromEpochMilliseconds(Long.MAX_VALUE) // Far future
            locationHistoryRepository.deleteLocationsBetween(startTime, endTime)
        } catch (e: Exception) {
            Napier.e("Failed to clear all location history", e)
            Result.failure(e)
        }
    }
}