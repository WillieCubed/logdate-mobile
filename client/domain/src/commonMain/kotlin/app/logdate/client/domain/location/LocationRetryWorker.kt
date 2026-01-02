package app.logdate.client.domain.location

import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.repository.location.LocationHistoryRepository
import app.logdate.shared.model.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Background worker that handles retrying failed location logging operations
 * with exponential backoff.
 */
class LocationRetryWorker(
    private val locationProvider: ClientLocationProvider,
    private val locationHistoryRepository: LocationHistoryRepository,
    private val coroutineScope: CoroutineScope
) {
    
    private val _pendingRetries = MutableStateFlow<List<PendingLocationLog>>(emptyList())
    val pendingRetries: StateFlow<List<PendingLocationLog>> = _pendingRetries.asStateFlow()
    
    private val activeJobs = mutableMapOf<String, Job>()
    
    companion object {
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val BASE_DELAY_SECONDS = 2.0
        private const val MAX_DELAY_SECONDS = 300.0 // 5 minutes max
    }
    
    /**
     * Schedules a location logging operation for retry with exponential backoff.
     */
    fun scheduleRetry(
        location: Location,
        userId: String,
        deviceId: String,
        originalTimestamp: Instant,
        attemptNumber: Int = 1
    ) {
        val pendingLog = PendingLocationLog(
            id = generateLogId(userId, deviceId, originalTimestamp),
            location = location,
            userId = userId,
            deviceId = deviceId,
            originalTimestamp = originalTimestamp,
            attemptNumber = attemptNumber,
            scheduledTime = Clock.System.now()
        )
        
        // Add to pending list
        _pendingRetries.value = _pendingRetries.value + pendingLog
        
        // Schedule the retry
        scheduleRetryJob(pendingLog)
    }
    
    private fun scheduleRetryJob(pendingLog: PendingLocationLog) {
        val delaySeconds = calculateDelay(pendingLog.attemptNumber)
        val delayDuration = delaySeconds.seconds
        
        val job = coroutineScope.launch {
            try {
                delay(delayDuration)
                
                // Attempt to log the location directly using repository
                val result = try {
                    val location = locationProvider.getCurrentLocation()
                    locationHistoryRepository.logLocation(
                        location = location,
                        userId = pendingLog.userId,
                        deviceId = pendingLog.deviceId,
                        confidence = 1.0f,
                        isGenuine = true
                    )
                } catch (e: Exception) {
                    Result.failure(e)
                }
                
                if (result.isSuccess) {
                    // Success - remove from pending list
                    removePendingLog(pendingLog.id)
                } else {
                    // Failed - schedule another retry if we haven't exceeded max attempts
                    handleRetryFailure(pendingLog)
                }
            } catch (e: Exception) {
                // Handle any unexpected errors
                handleRetryFailure(pendingLog)
            } finally {
                activeJobs.remove(pendingLog.id)
            }
        }
        
        activeJobs[pendingLog.id] = job
    }
    
    private fun handleRetryFailure(pendingLog: PendingLocationLog) {
        if (pendingLog.attemptNumber >= MAX_RETRY_ATTEMPTS) {
            // Max attempts reached - remove from pending list
            removePendingLog(pendingLog.id)
        } else {
            // Schedule another retry
            val nextAttempt = pendingLog.copy(
                attemptNumber = pendingLog.attemptNumber + 1,
                scheduledTime = Clock.System.now()
            )
            
            // Update pending list
            _pendingRetries.value = _pendingRetries.value.map { pending ->
                if (pending.id == pendingLog.id) nextAttempt else pending
            }
            
            scheduleRetryJob(nextAttempt)
        }
    }
    
    private fun removePendingLog(logId: String) {
        _pendingRetries.value = _pendingRetries.value.filterNot { it.id == logId }
        activeJobs[logId]?.cancel()
        activeJobs.remove(logId)
    }
    
    private fun calculateDelay(attemptNumber: Int): Double {
        val exponentialDelay = BASE_DELAY_SECONDS * (2.0.pow(attemptNumber - 1))
        return min(exponentialDelay, MAX_DELAY_SECONDS)
    }
    
    private fun generateLogId(userId: String, deviceId: String, timestamp: Instant): String {
        return "locationlog_${userId}_${deviceId}_${timestamp.toEpochMilliseconds()}"
    }
    
    /**
     * Cancels all pending retry operations.
     */
    fun cancelAllRetries() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        _pendingRetries.value = emptyList()
    }
    
    /**
     * Gets the current retry status for monitoring/debugging.
     */
    fun getRetryStatus(): RetryStatus {
        val pending = _pendingRetries.value
        return RetryStatus(
            totalPendingRetries = pending.size,
            nextRetryTime = pending.minByOrNull { it.scheduledTime }?.scheduledTime,
            failedAttempts = pending.sumOf { it.attemptNumber - 1 }
        )
    }
}

/**
 * Represents a location log operation pending retry.
 */
data class PendingLocationLog(
    val id: String,
    val location: Location,
    val userId: String,
    val deviceId: String,
    val originalTimestamp: Instant,
    val attemptNumber: Int,
    val scheduledTime: Instant
)

/**
 * Status information about the retry worker.
 */
data class RetryStatus(
    val totalPendingRetries: Int,
    val nextRetryTime: Instant?,
    val failedAttempts: Int
)
