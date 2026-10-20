package app.logdate.client.sync

import app.logdate.client.networking.DataUsageMode
import app.logdate.client.sync.metadata.UploadScope

internal enum class PeriodicNetworkRequirement {
    CONNECTED,
    UNMETERED,
}

internal sealed interface PeriodicSyncScheduleChange {
    data class Enable(
        val requirement: PeriodicNetworkRequirement,
        val ownerChanged: Boolean = false,
    ) : PeriodicSyncScheduleChange

    data object ResumeOwner : PeriodicSyncScheduleChange

    data object ResumeMedia : PeriodicSyncScheduleChange

    data object Disable : PeriodicSyncScheduleChange

    data object Unchanged : PeriodicSyncScheduleChange
}

internal class PeriodicSyncScheduleDecider {
    private var lastAuthenticatedScope: UploadScope? = null
    private var ownerChangePending = false
    private var authenticationBoundaryPending = false
    private var initialized = false
    private var lastMode: DataUsageMode? = null
    private var scheduledRequirement: PeriodicNetworkRequirement? = null

    fun next(
        authenticated: Boolean,
        mode: DataUsageMode,
        ownerScope: UploadScope? = null,
    ): PeriodicSyncScheduleChange {
        if (!authenticated) {
            authenticationBoundaryPending = true
            val change =
                if (!initialized || scheduledRequirement != null) {
                    PeriodicSyncScheduleChange.Disable
                } else {
                    PeriodicSyncScheduleChange.Unchanged
                }
            return change
        }

        if (ownerScope != null) {
            if (lastAuthenticatedScope != null && ownerScope != lastAuthenticatedScope) ownerChangePending = true
            lastAuthenticatedScope = ownerScope
        }
        val ownerChanged = ownerChangePending || authenticationBoundaryPending
        val requirement = requirement(mode)
        val change =
            if (initialized && scheduledRequirement == requirement) {
                if (ownerChanged) {
                    PeriodicSyncScheduleChange.ResumeOwner
                } else if (mode == DataUsageMode.Unrestricted &&
                    lastMode != mode
                ) {
                    PeriodicSyncScheduleChange.ResumeMedia
                } else {
                    PeriodicSyncScheduleChange.Unchanged
                }
            } else {
                PeriodicSyncScheduleChange.Enable(requirement, ownerChanged)
            }
        return change
    }

    fun acknowledge(
        authenticated: Boolean,
        mode: DataUsageMode,
        ownerScope: UploadScope? = null,
    ) {
        initialized = true
        scheduledRequirement = if (authenticated) requirement(mode) else null
        lastMode = mode.takeIf { authenticated }
        if (authenticated && ownerScope == lastAuthenticatedScope) {
            ownerChangePending = false
            authenticationBoundaryPending = false
        }
    }

    fun invalidateSchedule() {
        initialized = false
    }

    private fun requirement(mode: DataUsageMode): PeriodicNetworkRequirement =
        if (mode is DataUsageMode.Restricted) PeriodicNetworkRequirement.UNMETERED else PeriodicNetworkRequirement.CONNECTED
}
