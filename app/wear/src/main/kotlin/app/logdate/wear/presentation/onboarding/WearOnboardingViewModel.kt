package app.logdate.wear.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.wear.sync.WearDataLayerClient
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the onboarding phone connection check page.
 *
 * Checks whether a phone with LogDate is reachable via the
 * Wear Data Layer and exposes the result as [phoneCheckState].
 */
class WearOnboardingViewModel(
    private val dataLayerClient: WearDataLayerClient,
) : ViewModel() {

    private val _phoneCheckState = MutableStateFlow<PhoneCheckState>(PhoneCheckState.Checking)
    val phoneCheckState: StateFlow<PhoneCheckState> = _phoneCheckState.asStateFlow()

    init {
        checkPhoneConnection()
    }

    fun checkPhoneConnection() {
        _phoneCheckState.value = PhoneCheckState.Checking
        viewModelScope.launch {
            try {
                val connected = dataLayerClient.isPhoneConnected()
                _phoneCheckState.value = if (connected) {
                    PhoneCheckState.Connected
                } else {
                    PhoneCheckState.NotConnected
                }
            } catch (e: Exception) {
                Napier.w("Phone connection check failed during onboarding", e)
                _phoneCheckState.value = PhoneCheckState.NotConnected
            }
        }
    }
}

/**
 * State of the phone connection check during onboarding.
 */
sealed interface PhoneCheckState {
    data object Checking : PhoneCheckState
    data object Connected : PhoneCheckState
    data object NotConnected : PhoneCheckState
}
