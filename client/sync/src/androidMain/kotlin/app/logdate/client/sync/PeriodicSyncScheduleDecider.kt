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

    data object Disable : PeriodicSyncScheduleChange

    data object Unchanged : PeriodicSyncScheduleChange
}

internal class PeriodicSyncScheduleDecider {
    private var initialized = false
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
                PeriodicSyncScheduleChange.Unchanged
            } else {
                PeriodicSyncScheduleChange.Enable(requirement)
            }
        initialized = true
        scheduledRequirement = requirement
        return change
    }
}
