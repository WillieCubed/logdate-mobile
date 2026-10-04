package app.logdate.client.sync

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo

internal fun immediateSyncPolicy(states: List<WorkInfo.State>): ExistingWorkPolicy =
    if (WorkInfo.State.RUNNING in states) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE
