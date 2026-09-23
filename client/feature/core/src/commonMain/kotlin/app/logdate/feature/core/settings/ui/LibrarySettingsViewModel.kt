package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Reads and changes whether the Library tab is shown. */
class LibrarySettingsViewModel(
    private val preferencesDataSource: LogdatePreferencesDataSource,
) : ViewModel() {
    val isLibraryEnabled: StateFlow<Boolean> =
        preferencesDataSource
            .observeLibraryEnabled()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setLibraryEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesDataSource.setLibraryEnabled(enabled) }
    }
}
