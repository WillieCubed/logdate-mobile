package app.logdate.feature.location.timeline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.world.GetLocationUseCase
import app.logdate.client.domain.world.ObserveLocationUseCase
import app.logdate.client.domain.places.ResolveLocationToPlaceUseCase
import app.logdate.client.domain.places.PlaceResolutionResult
import app.logdate.client.domain.location.GetLocationHistoryUseCase
import app.logdate.client.domain.location.ObserveLocationHistoryUseCase
import app.logdate.client.domain.location.DeleteLocationEntryUseCase
import app.logdate.client.repository.location.LocationHistoryItem
import app.logdate.feature.location.timeline.ui.model.LocationTimelineItem
import app.logdate.feature.location.timeline.ui.model.LocationTimelineUiState
import app.logdate.shared.model.Location
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.days

/**
 * ViewModel for the location timeline screen.
 */
class LocationTimelineViewModel(
    private val getLocationUseCase: GetLocationUseCase,
    private val observeLocationUseCase: ObserveLocationUseCase,
    private val resolveLocationToPlaceUseCase: ResolveLocationToPlaceUseCase,
    private val getLocationHistoryUseCase: GetLocationHistoryUseCase,
    private val observeLocationHistoryUseCase: ObserveLocationHistoryUseCase,
    private val deleteLocationEntryUseCase: DeleteLocationEntryUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<LocationTimelineUiState>(LocationTimelineUiState.Loading)
    val uiState: StateFlow<LocationTimelineUiState> = _uiState.asStateFlow()
    
    init {
        loadLocationTimeline()
    }
    
    /**
     * Refreshes the location timeline data.
     * Useful when permissions are granted or user manually refreshes.
     */
    fun refreshData() {
        loadLocationTimeline()
    }
    
    private fun loadLocationTimeline() {
        // Combine current location and location history into a single flow
        viewModelScope.launch {
            try {
                // Start by showing loading state
                _uiState.value = LocationTimelineUiState.Loading
                
                // Combine current location and location history
                combine(
                    observeLocationUseCase()
                        .map { location -> location as Location? }
                        .catch { exception -> 
                            // Log the error but keep the flow alive
                            println("LocationTimelineViewModel: Error observing location - ${exception.message}")
                            emit(null)
                        }
                        .onStart { emit(null) }, // Start with null so we show loading state initially
                    observeLocationHistoryUseCase()
                        .catch { exception -> 
                            println("LocationTimelineViewModel: Error observing location history - ${exception.message}")
                            emit(emptyList()) 
                        }
                ) { currentLocation, locationHistory ->
                    val currentLocationItem = currentLocation?.let { location ->
                        try {
                            val placeResult = resolveLocationToPlaceUseCase(location)
                            locationToTimelineItem(location, placeResult, isCurrentLocation = true)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    
                    val historyItems = locationHistory.map { historyItem ->
                        historyItemToTimelineItem(historyItem)
                    }
                    
                    LocationTimelineUiState.Success(
                        currentLocation = currentLocationItem,
                        locationHistory = historyItems
                    )
                }.collect { newState ->
                    _uiState.value = newState
                }
            } catch (e: Exception) {
                _uiState.value = LocationTimelineUiState.Error(
                    message = e.message ?: "Unknown error occurred"
                )
            }
        }
    }
    
    fun deleteLocationEntry(locationId: String) {
        viewModelScope.launch {
            try {
                // Parse the locationId to extract userId, deviceId, and timestamp
                // Format: "userId:deviceId:timestamp"
                val parts = locationId.split(":")
                if (parts.size == 3) {
                    val userId = parts[0]
                    val deviceId = parts[1]
                    val timestamp = Instant.fromEpochMilliseconds(parts[2].toLong())
                    
                    deleteLocationEntryUseCase(userId, deviceId, timestamp)
                }
            } catch (e: Exception) {
                // Handle deletion errors - could show a snackbar or error message
            }
        }
    }
    
    private fun locationToTimelineItem(
        location: Location,
        placeResult: PlaceResolutionResult,
        isCurrentLocation: Boolean = false
    ): LocationTimelineItem {
        val now = Clock.System.now()
        val placeName = when (placeResult) {
            is PlaceResolutionResult.UserDefinedPlace -> placeResult.place.name
            is PlaceResolutionResult.ExternalSuggestion -> placeResult.suggestion.name
            is PlaceResolutionResult.UnknownLocation -> "Unknown Location"
        }
        
        val address = when (placeResult) {
            is PlaceResolutionResult.ExternalSuggestion -> placeResult.suggestion.address
            else -> "${location.latitude}, ${location.longitude}"
        }
        
        return LocationTimelineItem(
            id = if (isCurrentLocation) "current_location" else "current_${now.toEpochMilliseconds()}",
            placeName = placeName,
            address = address,
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = now.toEpochMilliseconds(),
            timeAgo = if (isCurrentLocation) "Now" else formatTimeAgo(now),
            isCurrentLocation = isCurrentLocation
        )
    }
    
    private suspend fun historyItemToTimelineItem(historyItem: LocationHistoryItem): LocationTimelineItem {
        val placeResult = resolveLocationToPlaceUseCase(historyItem.location)
        
        val placeName = when (placeResult) {
            is PlaceResolutionResult.UserDefinedPlace -> placeResult.place.name
            is PlaceResolutionResult.ExternalSuggestion -> placeResult.suggestion.name
            is PlaceResolutionResult.UnknownLocation -> "Unknown Location"
        }
        
        val address = when (placeResult) {
            is PlaceResolutionResult.ExternalSuggestion -> placeResult.suggestion.address
            else -> "${historyItem.location.latitude}, ${historyItem.location.longitude}"
        }
        
        return LocationTimelineItem(
            id = "${historyItem.userId}:${historyItem.deviceId}:${historyItem.timestamp.toEpochMilliseconds()}",
            placeName = placeName,
            address = address,
            latitude = historyItem.location.latitude,
            longitude = historyItem.location.longitude,
            timestamp = historyItem.timestamp.toEpochMilliseconds(),
            timeAgo = formatTimeAgo(historyItem.timestamp),
            isCurrentLocation = false
        )
    }
    
    private fun formatTimeAgo(timestamp: Instant): String {
        val now = Clock.System.now()
        val duration = now - timestamp
        
        return when {
            duration < 1.minutes -> "Just now"
            duration < 1.hours -> "${duration.inWholeMinutes}m ago"
            duration < 1.days -> "${duration.inWholeHours}h ago"
            else -> "${duration.inWholeDays}d ago"
        }
    }
    
    // TODO: Remove this mock data when real location history is implemented
    private fun generateMockLocationHistory(): List<LocationTimelineItem> {
        val now = Clock.System.now()
        return listOf(
            LocationTimelineItem(
                id = "1",
                placeName = "Home",
                address = "123 Main St, Anytown",
                latitude = 37.7749,
                longitude = -122.4194,
                timestamp = (now - 2.hours).toEpochMilliseconds(),
                timeAgo = "2h ago",
                duration = "8h 30m"
            ),
            LocationTimelineItem(
                id = "2",
                placeName = "Coffee Shop",
                address = "456 Oak Ave, Anytown",
                latitude = 37.7849,
                longitude = -122.4094,
                timestamp = (now - 4.hours).toEpochMilliseconds(),
                timeAgo = "4h ago",
                duration = "1h 15m"
            ),
            LocationTimelineItem(
                id = "3",
                placeName = "Work",
                address = "789 Business Blvd, Anytown",
                latitude = 37.7649,
                longitude = -122.4294,
                timestamp = (now - 1.days).toEpochMilliseconds(),
                timeAgo = "1d ago",
                duration = "9h 45m"
            )
        )
    }
}