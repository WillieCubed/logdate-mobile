package app.logdate.wear.location

import app.logdate.client.location.ClientLocationProvider
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.repository.journals.NoteCoordinates
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WearLocationCaptureCoordinatorTest {
    private val locationProvider = mockk<ClientLocationProvider>(relaxed = true)
    private val settingsRepository = mockk<LocationTrackingSettingsRepository>(relaxed = true)

    private fun coordinator(): WearLocationCaptureCoordinator =
        WearLocationCaptureCoordinator(locationProvider, settingsRepository)

    @Test
    fun `note location is null when journal auto-tagging is disabled`() =
        runTest {
            coEvery { settingsRepository.getSettings() } returns
                LocationTrackingSettings(autoTrackForJournalEntries = false)

            val location = coordinator().captureForJournalEntry()

            assertNull(location)
            coVerify(exactly = 0) { locationProvider.getCurrentLocation() }
        }

    @Test
    fun `note location is null when location permission is missing`() =
        runTest {
            coEvery { settingsRepository.getSettings() } returns LocationTrackingSettings(autoTrackForJournalEntries = true)
            every { locationProvider.hasLocationPermission() } returns false

            val location = coordinator().captureForJournalEntry()

            assertNull(location)
            coVerify(exactly = 0) { locationProvider.getCurrentLocation() }
        }

    @Test
    fun `note location contains current watch coordinates when enabled and permitted`() =
        runTest {
            coEvery { settingsRepository.getSettings() } returns LocationTrackingSettings(autoTrackForJournalEntries = true)
            every { locationProvider.hasLocationPermission() } returns true
            every { locationProvider.currentLocation } returns MutableSharedFlow()
            coEvery { locationProvider.getCurrentLocation() } returns
                Location(
                    latitude = 37.7749,
                    longitude = -122.4194,
                    altitude = LocationAltitude(14.5, AltitudeUnit.METERS),
                )

            val location = coordinator().captureForJournalEntry()

            assertEquals(
                NoteCoordinates(
                    latitude = 37.7749,
                    longitude = -122.4194,
                    altitude = 14.5,
                ),
                location?.coordinates,
            )
        }

    @Test
    fun `note location is null when provider fails`() =
        runTest {
            coEvery { settingsRepository.getSettings() } returns LocationTrackingSettings(autoTrackForJournalEntries = true)
            every { locationProvider.hasLocationPermission() } returns true
            every { locationProvider.currentLocation } returns MutableSharedFlow()
            coEvery { locationProvider.getCurrentLocation() } throws RuntimeException("gps unavailable")

            val location = coordinator().captureForJournalEntry()

            assertNull(location)
        }
}
