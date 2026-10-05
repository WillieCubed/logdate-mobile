package app.logdate.client.sync

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo

internal fun immediateSyncPolicy(
    states: List<WorkInfo.State>,
    hasMobileDataConsent: Boolean = false,
    preserveConsentedRequest: Boolean = false,
): ExistingWorkPolicy =
    when {
        preserveConsentedRequest && !hasMobileDataConsent -> ExistingWorkPolicy.KEEP
        WorkInfo.State.RUNNING !in states -> ExistingWorkPolicy.REPLACE
        hasMobileDataConsent -> ExistingWorkPolicy.APPEND_OR_REPLACE
        else -> ExistingWorkPolicy.KEEP
    }
