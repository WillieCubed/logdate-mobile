package app.logdate.client.launch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Records launch milestones as Compose state so the splash-release decision and any UI that
 * reads it recompose as stages complete.
 */
class LaunchStageTracker {
    var snapshot: LaunchStageSnapshot by mutableStateOf(LaunchStageSnapshot())
        private set

    val bootstrapState: LaunchBootstrapState
        get() = reduceLaunchBootstrapState(snapshot)

    /** Records [stage] and returns whether it advanced the snapshot. */
    fun mark(stage: LaunchStage): Boolean {
        val updated = snapshot.markCompleted(stage)
        if (updated == snapshot) return false
        snapshot = updated
        return true
    }

    fun expireWatchdog() {
        snapshot = snapshot.copy(hasWatchdogExpired = true)
    }
}
