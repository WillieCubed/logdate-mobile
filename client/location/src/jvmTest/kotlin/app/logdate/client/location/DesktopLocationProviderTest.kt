package app.logdate.client.location

import app.logdate.client.location.settings.DefaultLocation
import app.logdate.client.location.settings.LocationTrackingSettings
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.shared.model.AltitudeUnit
import app.logdate.shared.model.Location
import app.logdate.shared.model.LocationAltitude
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopLocationProviderTest {
    @Test
    fun `getCurrentLocation fails until default location is configured`() =
        runTest {
            val provider =
                DesktopLocationProvider(
                    settingsRepository = InMemoryLocationSettingsRepository(),
                    scope = backgroundScope,
                )

            assertFalse(provider.hasLocationPermission())
            assertFailsWith<UnsupportedOperationException> {
                provider.getCurrentLocation()
            }
        }

    @Test
    fun `getCurrentLocation returns configured default location`() =
        runTest {
            val location =
                Location(
                    latitude = 51.5072,
                    longitude = -0.1276,
                    altitude = LocationAltitude(35.0, AltitudeUnit.METERS),
                )
            val provider =
                DesktopLocationProvider(
                    settingsRepository =
                        InMemoryLocationSettingsRepository(
                            LocationTrackingSettings(defaultLocation = DefaultLocation.fromLocation(location)),
                        ),
                    scope = backgroundScope,
                )

            advanceUntilIdle()

            assertTrue(provider.hasLocationPermission())
            assertEquals(location, provider.getCurrentLocation())
        }

    @Test
    fun `currentLocation emits configured default location from settings updates`() =
        runTest {
            val repository = InMemoryLocationSettingsRepository()
            val provider =
                DesktopLocationProvider(
                    settingsRepository = repository,
                    scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
                )
            val location =
                Location(
                    latitude = 51.5072,
                    longitude = -0.1276,
                    altitude = LocationAltitude(35.0, AltitudeUnit.METERS),
                )
            val received = mutableListOf<Location>()
            val job = launch { received += provider.currentLocation.first() }

            repository.updateSettings(
                LocationTrackingSettings(defaultLocation = DefaultLocation.fromLocation(location)),
            )
            advanceUntilIdle()

            assertEquals(listOf(location), received)
            job.cancel()
        }

    @Test
    fun `observing settings failure does not prevent one shot location reads`() =
        runTest {
            val location =
                Location(
                    latitude = 51.5072,
                    longitude = -0.1276,
                    altitude = LocationAltitude(35.0, AltitudeUnit.METERS),
                )
            val provider =
                DesktopLocationProvider(
                    settingsRepository =
                        ThrowingObserveLocationSettingsRepository(
                            LocationTrackingSettings(defaultLocation = DefaultLocation.fromLocation(location)),
                        ),
                    scope = backgroundScope,
                )

            assertEquals(location, provider.getCurrentLocation())
        }
}

private class InMemoryLocationSettingsRepository(
    initialSettings: LocationTrackingSettings = LocationTrackingSettings(),
) : LocationTrackingSettingsRepository {
    private val settings = MutableStateFlow(initialSettings)

    override suspend fun getSettings(): LocationTrackingSettings = settings.value

    override fun observeSettings(): Flow<LocationTrackingSettings> = settings

    override suspend fun updateSettings(settings: LocationTrackingSettings) {
        this.settings.value = settings
    }

    override suspend fun setBackgroundTrackingEnabled(enabled: Boolean) {
        updateSettings(settings.value.copy(backgroundTrackingEnabled = enabled))
    }
}

private class ThrowingObserveLocationSettingsRepository(
    private val currentSettings: LocationTrackingSettings,
) : LocationTrackingSettingsRepository {
    override suspend fun getSettings(): LocationTrackingSettings = currentSettings

    override fun observeSettings(): Flow<LocationTrackingSettings> = throw IllegalStateException("settings unavailable")

    override suspend fun updateSettings(settings: LocationTrackingSettings) = Unit

    override suspend fun setBackgroundTrackingEnabled(enabled: Boolean) = Unit
}
