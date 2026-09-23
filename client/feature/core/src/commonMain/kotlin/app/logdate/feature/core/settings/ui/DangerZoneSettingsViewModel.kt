package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class DangerZoneSettingsViewModel(
    private val deviceEraser: DeviceEraser,
) : ViewModel() {
    fun clearLocalData(
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            deviceEraser
                .clearJournal()
                .onSuccess { onSuccess() }
                .onFailure { onError(it.message ?: "Unknown error") }
        }
    }

    fun resetApp(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            deviceEraser.eraseEverything()
            onComplete?.invoke()
        }
    }
}
