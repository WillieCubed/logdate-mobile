package app.logdate.feature.core.settings.ui

import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.sync_account_waiting
import logdate.client.feature.core.generated.resources.sync_feedback_up_to_date
import logdate.client.feature.core.generated.resources.syncing
import org.jetbrains.compose.resources.StringResource

internal fun CloudArchiveStatus.messageResource(): StringResource =
    when (phase) {
        CloudArchivePhase.RUNNING -> Res.string.syncing
        CloudArchivePhase.COMPLETE -> if (lastCompletedAt != null) Res.string.sync_feedback_up_to_date else Res.string.sync_account_waiting
        else -> Res.string.sync_account_waiting
    }
