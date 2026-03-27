package app.logdate.feature.core.restore

import app.logdate.client.domain.restore.RestoreProgressPhase

internal fun RestoreProgressPhase.toProgressInfo(): RestoreProgressInfo = toRestoreStage().toProgressInfo()

internal fun RestoreStage.toProgressInfo(progressPercent: Int = defaultProgressPercent): RestoreProgressInfo =
    RestoreProgressInfo.Active(stage = this, progressPercent = progressPercent)

private fun RestoreProgressPhase.toRestoreStage(): RestoreStage =
    when (this) {
        RestoreProgressPhase.RESTORING_JOURNALS -> RestoreStage.RESTORING_JOURNALS
        RestoreProgressPhase.RESTORING_NOTES -> RestoreStage.RESTORING_NOTES
        RestoreProgressPhase.RESTORING_LINKS -> RestoreStage.RESTORING_LINKS
        RestoreProgressPhase.RESTORING_DRAFTS -> RestoreStage.RESTORING_DRAFTS
        RestoreProgressPhase.RESTORING_PROFILE -> RestoreStage.RESTORING_PROFILE
        RestoreProgressPhase.RESTORING_PLACES -> RestoreStage.RESTORING_PLACES
        RestoreProgressPhase.RESTORING_LOCATION_HISTORY -> RestoreStage.RESTORING_LOCATION_HISTORY
    }
