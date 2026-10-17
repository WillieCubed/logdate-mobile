package app.logdate.client.sync

import app.logdate.client.networking.DataUsageMode

internal enum class PeriodicNetworkRequirement {
    CONNECTED,
    UNMETERED,
}

internal sealed interface PeriodicSyncScheduleChange {
    data class Enable(
        val requirement: PeriodicNetworkRequirement,
    ) : PeriodicSyncScheduleChange

    data object ResumeMedia : PeriodicSyncScheduleChange

    data object Disable : PeriodicSyncScheduleChange

    data object Unchanged : PeriodicSyncScheduleChange
}

internal class PeriodicSyncScheduleDecider {
    private var initialized = false
    private var lastMode: DataUsageMode? = null
    private var scheduledRequirement: PeriodicNetworkRequirement? = null

    fun next(
        authenticated: Boolean,
        mode: DataUsageMode,
    ): PeriodicSyncScheduleChange {
        if (!authenticated) {
            val change =
                if (!initialized || scheduledRequirement != null) {
                    PeriodicSyncScheduleChange.Disable
                } else {
                    PeriodicSyncScheduleChange.Unchanged
                }
            initialized = true
            scheduledRequirement = null
            lastMode = null
            return change
        }

        val requirement =
            if (mode is DataUsageMode.Restricted) {
                PeriodicNetworkRequirement.UNMETERED
            } else {
                PeriodicNetworkRequirement.CONNECTED
            }
        val change =
            if (initialized && scheduledRequirement == requirement) {
                if (mode == DataUsageMode.Unrestricted &&
                    lastMode != mode
                ) {
                    PeriodicSyncScheduleChange.ResumeMedia
                } else {
                    PeriodicSyncScheduleChange.Unchanged
                }
            } else {
                PeriodicSyncScheduleChange.Enable(requirement)
            }
        initialized = true
        scheduledRequirement = requirement
        lastMode = mode
        return change
    }
}
