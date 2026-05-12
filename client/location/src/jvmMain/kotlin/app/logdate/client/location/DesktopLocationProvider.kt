package app.logdate.client.location

import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.shared.model.Location
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Desktop [ClientLocationProvider].
 *
 * Desktop builds do not have a trustworthy OS location provider wired today, so this class
 * never fabricates coordinates. Location-dependent features use the user-configured default
 * location from settings, or receive an explicit unsupported error from one-shot requests when no
 * default is configured.
 *
 * @param settingsRepository Repository that stores the optional user-configured default location.
 * @param scope Application-level scope used to observe settings changes for the lifetime of the
 *   desktop location provider.
 */
class DesktopLocationProvider(
    private val settingsRepository: LocationTrackingSettingsRepository,
    private val scope: CoroutineScope,
) : ClientLocationProvider {
    private val locationFlowState = MutableSharedFlow<Location>(replay = 1)
    private val defaultLocationState = MutableStateFlow<Location?>(null)

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                settingsRepository
                    .observeSettings()
                    .catch { error -> Napier.w("Desktop location settings updates stopped", error) }
                    .collect { settings ->
                        val location = settings.defaultLocation?.toLocation()
                        defaultLocationState.value = location
                        if (location != null) {
                            locationFlowState.emit(location)
                        }
                    }
            } catch (error: Exception) {
                Napier.w("Desktop location settings updates could not start", error)
            }
        }
    }

    override val currentLocation: SharedFlow<Location> = locationFlowState.asSharedFlow()

    /**
     * Desktop has no runtime location permission prompt. For interface compatibility, this returns
     * whether a user-configured default location is available.
     */
    override fun hasLocationPermission(): Boolean = defaultLocationState.value != null

    override suspend fun getCurrentLocation(): Location =
        settingsRepository
            .getSettings()
            .defaultLocation
            ?.toLocation()
            ?: throw UnsupportedOperationException("Configure a default location before using location features on this desktop build.")

    override suspend fun refreshLocation() = locationFlowState.emit(getCurrentLocation())
}
