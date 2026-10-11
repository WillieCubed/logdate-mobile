package app.logdate.wear.v2.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.location.settings.LocationTrackingSettingsRepository
import app.logdate.client.permissions.PermissionManager
import app.logdate.client.permissions.PermissionType
import app.logdate.client.sync.SyncManager
import app.logdate.client.sync.datalayer.v2.WearFeatures
import app.logdate.client.sync.datalayer.v2.WearProtocolGate
import app.logdate.wear.sync.PhoneReachability
import app.logdate.wear.v2.haptics.HapticsPreference
import app.logdate.wear.v2.haptics.HapticsPreferenceStore
import app.logdate.wear.v2.phone.HandoffResult
import app.logdate.wear.v2.phone.PhoneHandoff
import app.logdate.wear.v2.phone.PhoneLink
import app.logdate.wear.v2.phone.PhoneLinkRepository
import app.logdate.wear.v2.today.HandoffState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the settings section shows.
 *
 * @property places Captures save the place: the setting is on and location access is granted.
 * @property phoneSettingsNodeId The phone "More on your phone" opens, or null while it cannot.
 */
data class WatchSettingsUiState(
    val status: WatchStatus = WatchStatus.Healthy,
    val haptics: HapticsPreference = HapticsPreference.ALL,
    val places: Boolean = false,
    val phoneSettingsNodeId: String? = null,
    val handoff: HandoffState = HandoffState.IDLE,
    val retrying: Boolean = false,
)

/**
 * The settings at the end of the moment list. Every input is followed as it changes; only location
 * access is read again on resume, since it can change in system settings without an event.
 */
class WatchSettingsViewModel(
    private val syncManager: SyncManager,
    phoneReachability: PhoneReachability,
    private val haptics: HapticsPreferenceStore,
    private val locationSettings: LocationTrackingSettingsRepository,
    private val permissionManager: PermissionManager,
    phoneLinks: PhoneLinkRepository,
    protocolGate: WearProtocolGate,
    private val phoneHandoff: PhoneHandoff,
) : ViewModel() {
    private val locationGranted = MutableStateFlow(permissionManager.isPermissionGranted(PermissionType.LOCATION))
    private val handoff = MutableStateFlow(HandoffState.IDLE)
    private val retrying = MutableStateFlow(false)

    private val status = combine(syncManager.syncStatusFlow, phoneReachability.observe(), ::watchStatusOf)
    private val places =
        combine(locationSettings.observeSettings(), locationGranted) { settings, granted -> settings.autoTrackForJournalEntries && granted }
    private val phoneSettingsNode =
        combine(protocolGate.isFeatureActive(WearFeatures.PHONE_SETTINGS_LINK), phoneLinks.observe()) { active, link ->
            (link as? PhoneLink.Connected)?.nodeId?.takeIf { active }
        }

    val uiState: StateFlow<WatchSettingsUiState> =
        combine(
            status,
            haptics.preference,
            places,
            phoneSettingsNode,
            combine(handoff, retrying, ::Pair),
        ) { syncStatus, hapticsChoice, placesOn, node, (handoffState, isRetrying) ->
            WatchSettingsUiState(
                status = syncStatus,
                haptics = hapticsChoice,
                places = placesOn,
                phoneSettingsNodeId = node,
                handoff = handoffState,
                retrying = isRetrying,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), WatchSettingsUiState())

    fun setHaptics(preference: HapticsPreference) = haptics.set(preference)

    fun setPlaces(enabled: Boolean) {
        viewModelScope.launch {
            try {
                locationSettings.updateSettings(locationSettings.getSettings().copy(autoTrackForJournalEntries = enabled))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.e("Couldn't turn places ${if (enabled) "on" else "off"} on the watch", e)
            }
        }
    }

    /** Whether turning places on first needs location access. */
    fun placesNeedPermission(): Boolean = !permissionManager.isPermissionGranted(PermissionType.LOCATION)

    fun onLocationPermissionResult(granted: Boolean) {
        locationGranted.value = granted
        if (granted) setPlaces(true)
    }

    /** Reads location access again; it may have changed in system settings. */
    fun onResume() {
        locationGranted.value = permissionManager.isPermissionGranted(PermissionType.LOCATION)
    }

    /** Sends what is waiting again, once at a time. */
    fun retrySync() {
        if (retrying.value) return
        retrying.value = true
        viewModelScope.launch {
            try {
                syncManager.fullSync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Napier.w("Sending moments to the phone again failed", e)
            } finally {
                retrying.value = false
            }
        }
    }

    /** Opens the phone's Watch page on the phone. */
    fun openPhoneSettings() {
        val node = uiState.value.phoneSettingsNodeId ?: return
        if (handoff.value == HandoffState.SENDING) return
        handoff.value = HandoffState.SENDING
        viewModelScope.launch {
            handoff.value =
                when (phoneHandoff.open(PHONE_WATCH_SETTINGS_LINK, node)) {
                    HandoffResult.SENT -> HandoffState.SENT
                    HandoffResult.FAILED -> HandoffState.FAILED
                }
        }
    }

    fun dismissHandoff() {
        handoff.value = HandoffState.IDLE
    }

    companion object {
        const val PHONE_WATCH_SETTINGS_LINK = "studio.hypertext.logdate://settings/watch"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
