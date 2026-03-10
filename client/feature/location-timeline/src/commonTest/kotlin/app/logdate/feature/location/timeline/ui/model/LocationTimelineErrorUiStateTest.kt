package app.logdate.feature.location.timeline.ui.model

import kotlin.test.Test
import kotlin.test.assertEquals

class LocationTimelineErrorUiStateTest {
    private class SecurityException(
        message: String,
    ) : Exception(message)

    @Test
    fun `maps security exceptions to permission required`() {
        val error = SecurityException("Location permission not granted")

        assertEquals(LocationTimelineErrorUiState.PermissionRequired, error.toLocationTimelineErrorUiState())
    }

    @Test
    fun `maps disabled location service failures to location services disabled`() {
        val error = IllegalStateException("Unable to get current location")

        assertEquals(LocationTimelineErrorUiState.LocationServicesDisabled, error.toLocationTimelineErrorUiState())
    }

    @Test
    fun `maps unexpected failures to temporarily unavailable`() {
        val error = IllegalArgumentException("boom")

        assertEquals(LocationTimelineErrorUiState.TemporarilyUnavailable, error.toLocationTimelineErrorUiState())
    }
}
