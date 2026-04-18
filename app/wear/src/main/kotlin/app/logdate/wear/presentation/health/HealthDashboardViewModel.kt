package app.logdate.wear.presentation.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.logdate.client.database.dao.HealthSnapshotDao
import app.logdate.client.database.entities.HealthSnapshotEntity
import app.logdate.wear.health.WearHealthSensorManager
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HealthDashboardUiState(
    val currentHeartRate: Int? = null,
    val currentStepCount: Int? = null,
    val recentSnapshots: List<HealthSnapshotEntity> = emptyList(),
    val correlationInsight: String = "",
)

class HealthDashboardViewModel(
    private val healthSensorManager: WearHealthSensorManager,
    private val healthSnapshotDao: HealthSnapshotDao,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HealthDashboardUiState())
    val uiState: StateFlow<HealthDashboardUiState> = _uiState.asStateFlow()

    init {
        loadCurrentReadings()
        observeRecentSnapshots()
    }

    private fun loadCurrentReadings() {
        viewModelScope.launch {
            try {
                val snapshot = healthSensorManager.sampleCurrent()
                _uiState.update {
                    it.copy(
                        currentHeartRate = snapshot.heartRateBpm,
                        currentStepCount = snapshot.stepCount,
                    )
                }
            } catch (e: Exception) {
                Napier.w("Failed to load current health readings", e)
            }
        }
    }

    private fun observeRecentSnapshots() {
        viewModelScope.launch {
            healthSnapshotDao.observeRecent(limit = 10).collect { snapshots ->
                val insight = generateCorrelationInsight(snapshots)
                _uiState.update {
                    it.copy(
                        recentSnapshots = snapshots,
                        correlationInsight = insight,
                    )
                }
            }
        }
    }

    private fun generateCorrelationInsight(snapshots: List<HealthSnapshotEntity>): String {
        if (snapshots.isEmpty()) return ""

        val withHeartRate = snapshots.mapNotNull { it.heartRateBpm }
        if (withHeartRate.isEmpty()) return ""

        val avgHr = withHeartRate.average().toInt()
        return "Average HR while journaling: $avgHr bpm across ${snapshots.size} entries"
    }
}
