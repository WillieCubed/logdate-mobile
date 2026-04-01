package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.domain.streak.ObserveStreakUseCase
import app.logdate.client.domain.streak.RefreshStreakUseCase
import app.logdate.client.domain.streak.SetStreakEnabledUseCase
import app.logdate.client.domain.streak.StreakData
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StreakSettingsViewModel(
    observeStreakUseCase: ObserveStreakUseCase,
    private val setStreakEnabledUseCase: SetStreakEnabledUseCase,
    private val refreshStreakUseCase: RefreshStreakUseCase,
) : ViewModel() {
    data class UiState(
        val streakData: StreakData = StreakData(),
    )

    val uiState: StateFlow<UiState> =
        observeStreakUseCase()
            .map { UiState(streakData = it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UiState(),
            )

    init {
        viewModelScope.launch {
            refreshStreakUseCase()
        }
    }

    fun toggleStreakTracking(enabled: Boolean) {
        viewModelScope.launch {
            try {
                setStreakEnabledUseCase(enabled)
            } catch (e: Exception) {
                Napier.e("Failed to toggle streak tracking", e)
            }
        }
    }
}
