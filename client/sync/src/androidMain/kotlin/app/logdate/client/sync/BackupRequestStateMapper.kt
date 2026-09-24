package app.logdate.client.sync

import androidx.work.WorkInfo

internal data class BackupWorkSnapshot(
    val state: WorkInfo.State,
    val runAttemptCount: Int,
)

internal fun backupRequestState(work: List<BackupWorkSnapshot>): BackupRequestState =
    when {
        work.any { it.state == WorkInfo.State.RUNNING } -> BackupRequestState.RUNNING
        work.any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0 } -> BackupRequestState.RETRYING
        work.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } -> BackupRequestState.QUEUED
        work.any { it.state == WorkInfo.State.FAILED } -> BackupRequestState.FAILED
        work.any { it.state == WorkInfo.State.SUCCEEDED } -> BackupRequestState.COMPLETED
        else -> BackupRequestState.NONE
    }
