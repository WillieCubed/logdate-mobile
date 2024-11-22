package app.logdate.feature.library.ui.location

data class LocationLogUiState(
    /**
     * The location log entries.
     */
    val records: List<LocationLogEntryUiState>,
)