package app.logdate.client.domain.location

import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.repository.location.LocationHistoryRepository
import kotlinx.datetime.Clock

/**
 * Use case to log the current location to location history with automatic
 * retry functionality using exponential backoff for failed attempts.
 * 
 * This is typically called when significant events occur (like creating notes)
 * to maintain a timeline of where the user was when they performed actions.
 */
class LogCurrentLocationUseCase(
    private val locationProvider: ClientLocationProvider,
    private val locationHistoryRepository: LocationHistoryRepository,
    private val locationRetryWorker: LocationRetryWorker,
    // TODO: Get actual user and device IDs from user session/device repository
    private val defaultUserId: String = "user_1",
    private val defaultDeviceId: String = "device_1"
) {
    
    /**
     * Handles location logging operations based on the request type
     */
    suspend operator fun invoke(request: LocationLogRequest): LocationLogResult {
        return when (request) {
            is LocationLogRequest.LogLocation -> {
                try {
                    // First, try to log immediately
                    val location = locationProvider.getCurrentLocation()
                    val result = locationHistoryRepository.logLocation(
                        location = location,
                        userId = request.userId,
                        deviceId = request.deviceId,
                        confidence = 1.0f,
                        isGenuine = true
                    )
                    
                    if (result.isSuccess) {
                        // Success on first try
                        LocationLogResult.LogSuccess
                    } else {
                        // Failed - schedule background retry
                        scheduleBackgroundRetry(request.userId, request.deviceId)
                        // Return success since we've handled the failure gracefully
                        LocationLogResult.LogSuccess
                    }
                } catch (e: Exception) {
                    // Handle any unexpected errors by scheduling retry
                    try {
                        scheduleBackgroundRetry(request.userId, request.deviceId)
                        LocationLogResult.LogSuccess
                    } catch (retryError: Exception) {
                        LocationLogResult.LogFailure(retryError)
                    }
                }
            }
            is LocationLogRequest.GetRetryStatus -> {
                val status = locationRetryWorker.getRetryStatus()
                LocationLogResult.RetryStatusInfo(status)
            }
            is LocationLogRequest.CancelAllRetries -> {
                locationRetryWorker.cancelAllRetries()
                LocationLogResult.RetriesCancelled
            }
        }
    }
    
    
    private suspend fun scheduleBackgroundRetry(
        userId: String,
        deviceId: String
    ) {
        try {
            // Get current location for the retry
            val location = locationProvider.getCurrentLocation()
            val timestamp = Clock.System.now()
            
            // Schedule background retry
            locationRetryWorker.scheduleRetry(
                location = location,
                userId = userId,
                deviceId = deviceId,
                originalTimestamp = timestamp,
                attemptNumber = 1
            )
        } catch (e: Exception) {
            // If we can't even get the location, we can't schedule a retry
            throw e
        }
    }
    
    sealed class LocationLogRequest {
        data class LogLocation(
            val userId: String = "user_1",
            val deviceId: String = "device_1"
        ) : LocationLogRequest()
        
        object GetRetryStatus : LocationLogRequest()
        object CancelAllRetries : LocationLogRequest()
    }
    
    sealed class LocationLogResult {
        object LogSuccess : LocationLogResult()
        data class LogFailure(val error: Throwable) : LocationLogResult()
        data class RetryStatusInfo(val status: RetryStatus) : LocationLogResult()
        object RetriesCancelled : LocationLogResult()
    }
}