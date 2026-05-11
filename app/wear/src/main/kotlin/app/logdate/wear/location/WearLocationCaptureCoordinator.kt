package app.logdate.wear.location

import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.shared.model.Location
import io.github.aakira.napier.Napier

/**
 * Centralizes Wear journal-entry geotagging policy.
 *
 * Capture flows should ask this coordinator for an optional [NoteLocation] instead of
 * handling permission, settings, and provider failures independently.
 */
class WearLocationCaptureCoordinator(
    private val locationProvider: ClientLocationProvider,
    private val settingsRepository: LocationTrackingSettingsRepository,
) {
    suspend fun captureForJournalEntry(): NoteLocation? {
        val settings = settingsRepository.getSettings()
        if (!settings.autoTrackForJournalEntries) return null
        if (!locationProvider.hasLocationPermission()) return null

        return runCatching { locationProvider.getCurrentLocation().toNoteLocation() }
            .onFailure { error -> Napier.w("Wear journal location capture failed", error) }
            .getOrNull()
    }
}

private fun Location.toNoteLocation(): NoteLocation =
    NoteLocation(
        coordinates =
            NoteCoordinates(
                latitude = latitude,
                longitude = longitude,
                altitude = altitude.value,
            ),
    )
