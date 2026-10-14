package app.logdate.wear.location

import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.client.repository.journals.NoteLocation
import app.logdate.shared.model.Location
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException

/**
 * Centralizes Wear journal-entry geotagging policy.
 *
 * Capture flows should ask this coordinator for an optional [NoteLocation] instead of
 * handling permission, settings, and provider failures independently.
 */
class WearLocationCaptureCoordinator(
    private val locationProvider: ClientLocationProvider,
    private val settingsRepository: LocationTrackingSettingsRepository,
    private val captureEnabled: suspend () -> Boolean? = { null },
) {
    suspend fun captureForJournalEntry(): NoteLocation? {
        if (!isCaptureEnabled()) return null
        if (!locationProvider.hasLocationPermission()) return null

        return try {
            val location = locationProvider.getCurrentLocation()
            if (isCaptureEnabled() && locationProvider.hasLocationPermission()) location.toNoteLocation() else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Wear journal location capture failed", e)
            null
        }
    }

    private suspend fun isCaptureEnabled(): Boolean = captureEnabled() ?: settingsRepository.getSettings().autoTrackForJournalEntries
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
