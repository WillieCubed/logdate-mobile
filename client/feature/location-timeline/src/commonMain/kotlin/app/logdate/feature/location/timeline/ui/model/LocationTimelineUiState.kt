package app.logdate.feature.location.timeline.ui.model

/**
 * UI state for the location timeline screen.
 */
sealed class LocationTimelineUiState {
    
    /**
     * Loading location timeline data.
     */
    data object Loading : LocationTimelineUiState()
    
    /**
     * Successfully loaded location timeline.
     * 
     * Note: allLocations is already sorted in reverse chronological order (newest first)
     */
    data class Success(
        val currentLocation: LocationTimelineItem?, // Kept for backward compatibility
        val locationHistory: List<LocationTimelineItem>, // Kept for backward compatibility
        val allLocations: List<LocationTimelineItem> = buildLocationsList(currentLocation, locationHistory)
    ) : LocationTimelineUiState()
    
    /**
     * Error loading location timeline.
     */
    data class Error(
        val message: String
    ) : LocationTimelineUiState()
    
    companion object {
        /**
         * Builds a consolidated list of all locations in reverse chronological order.
         * Current location (if available) will always be first.
         */
        private fun buildLocationsList(
            currentLocation: LocationTimelineItem?,
            locationHistory: List<LocationTimelineItem>
        ): List<LocationTimelineItem> {
            val result = mutableListOf<LocationTimelineItem>()
            
            // Add current location first if available
            currentLocation?.let { result.add(it) }
            
            // Add historical locations, sorted by timestamp (newest first)
            result.addAll(locationHistory.sortedByDescending { it.timestamp })
            
            return result
        }
    }
}

/**
 * UI model for a location timeline item.
 */
data class LocationTimelineItem(
    val id: String,
    val placeName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val timeAgo: String,
    val duration: String? = null,
    val isCurrentLocation: Boolean = false
)