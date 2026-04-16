package app.logdate.feature.core.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.domain.dayboundary.DayBoundarySettings
import app.logdate.client.domain.dayboundary.DayBoundarySettingsRepository
import app.logdate.client.domain.dayboundary.HealthConnectStatus
import app.logdate.client.domain.dayboundary.ObserveHealthConnectStatusUseCase
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DayBoundarySettingsViewModel(
    private val settingsRepository: DayBoundarySettingsRepository,
    private val preferencesDataSource: LogdatePreferencesDataSource,
    private val observeHealthConnectStatus: ObserveHealthConnectStatusUseCase,
) : ViewModel() {
    data class UiState(
        val dayBoundarySettings: DayBoundarySettings = DayBoundarySettings(),
        val fallbackStartHour: Int = DEFAULT_FALLBACK_START_HOUR,
        val healthConnectStatus: HealthConnectStatus = HealthConnectStatus.CHECKING,
    )

    private val healthStatus = MutableStateFlow(HealthConnectStatus.CHECKING)
    private var healthStatusJob: Job? = null

    val uiState: StateFlow<UiState> =
        combine(
            settingsRepository.observeSettings(),
            preferencesDataSource.observeDayStartHour(),
            healthStatus,
        ) { settings, startHour, status ->
            UiState(
                dayBoundarySettings = settings,
                fallbackStartHour = startHour ?: DEFAULT_FALLBACK_START_HOUR,
                healthConnectStatus = status,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState(),
        )

    init {
        refreshHealthStatus()
    }

    fun toggleSleepBasedBoundaries(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settingsRepository.setSleepBasedBoundariesEnabled(enabled)
            } catch (e: Exception) {
                Napier.e("Failed to toggle sleep-based boundaries", e)
            }
        }
    }

    fun setFallbackStartHour(hour: Int) {
        viewModelScope.launch {
            try {
                preferencesDataSource.setDayBounds(startHour = hour, endHour = hour)
            } catch (e: Exception) {
                Napier.e("Failed to set fallback start hour", e)
            }
        }
    }

    fun refreshHealthStatus() {
        healthStatusJob?.cancel()
        healthStatusJob =
            viewModelScope.launch {
                observeHealthConnectStatus().collect { status ->
                    healthStatus.value = status
                }
            }
    }

    companion object {
        private const val DEFAULT_FALLBACK_START_HOUR = 4
    }
}
