package app.logdate.feature.core.sync

import app.logdate.shared.model.diagnostics.DiagnosticOutcome
import app.logdate.shared.model.diagnostics.DiagnosticReason
import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.sync_diagnostic_outcome_conflict
import logdate.client.feature.core.generated.resources.sync_diagnostic_outcome_failed
import logdate.client.feature.core.generated.resources.sync_diagnostic_outcome_interrupted
import logdate.client.feature.core.generated.resources.sync_diagnostic_outcome_paused
import logdate.client.feature.core.generated.resources.sync_diagnostic_outcome_queued
import logdate.client.feature.core.generated.resources.sync_diagnostic_outcome_retry_scheduled
import logdate.client.feature.core.generated.resources.sync_diagnostic_outcome_started
import logdate.client.feature.core.generated.resources.sync_diagnostic_outcome_succeeded
import logdate.client.feature.core.generated.resources.sync_diagnostic_reason_conflict
import logdate.client.feature.core.generated.resources.sync_diagnostic_reason_corrupt_payload
import logdate.client.feature.core.generated.resources.sync_diagnostic_reason_incompatible_server
import logdate.client.feature.core.generated.resources.sync_diagnostic_reason_key_recovery_required
import logdate.client.feature.core.generated.resources.sync_diagnostic_reason_local_storage
import logdate.client.feature.core.generated.resources.sync_diagnostic_reason_missing_media
import logdate.client.feature.core.generated.resources.sync_diagnostic_reason_none
import logdate.client.feature.core.generated.resources.sync_diagnostic_reason_offline
import logdate.client.feature.core.generated.resources.sync_diagnostic_reason_persistence_failed
import logdate.client.feature.core.generated.resources.sync_diagnostic_reason_quota_exceeded
import logdate.client.feature.core.generated.resources.sync_diagnostic_reason_rate_limited
import logdate.client.feature.core.generated.resources.sync_diagnostic_reason_server_unavailable
import logdate.client.feature.core.generated.resources.sync_diagnostic_reason_sign_in_required
import logdate.client.feature.core.generated.resources.sync_diagnostic_reason_unknown
import logdate.client.feature.core.generated.resources.sync_diagnostic_reason_unsupported_format
import logdate.client.feature.core.generated.resources.sync_diagnostic_retry_automatic
import logdate.client.feature.core.generated.resources.sync_diagnostic_retry_free_space
import logdate.client.feature.core.generated.resources.sync_diagnostic_retry_key_recovery
import logdate.client.feature.core.generated.resources.sync_diagnostic_retry_network
import logdate.client.feature.core.generated.resources.sync_diagnostic_retry_review_conflict
import logdate.client.feature.core.generated.resources.sync_diagnostic_retry_sign_in
import logdate.client.feature.core.generated.resources.sync_diagnostic_retry_unknown
import logdate.client.feature.core.generated.resources.sync_diagnostic_retry_update_app
import logdate.client.feature.core.generated.resources.sync_diagnostic_retry_update_server
import logdate.client.feature.core.generated.resources.sync_diagnostic_retry_user_retry
import org.jetbrains.compose.resources.StringResource

internal fun DiagnosticOutcome.outcomeResource(): StringResource =
    when (this) {
        DiagnosticOutcome.QUEUED -> Res.string.sync_diagnostic_outcome_queued
        DiagnosticOutcome.STARTED -> Res.string.sync_diagnostic_outcome_started
        DiagnosticOutcome.SUCCEEDED -> Res.string.sync_diagnostic_outcome_succeeded
        DiagnosticOutcome.FAILED -> Res.string.sync_diagnostic_outcome_failed
        DiagnosticOutcome.RETRY_SCHEDULED -> Res.string.sync_diagnostic_outcome_retry_scheduled
        DiagnosticOutcome.INTERRUPTED -> Res.string.sync_diagnostic_outcome_interrupted
        DiagnosticOutcome.CONFLICT -> Res.string.sync_diagnostic_outcome_conflict
        DiagnosticOutcome.PAUSED -> Res.string.sync_diagnostic_outcome_paused
    }

internal fun DiagnosticReason.reasonResource(): StringResource =
    when (this) {
        DiagnosticReason.NONE -> Res.string.sync_diagnostic_reason_none
        DiagnosticReason.OFFLINE -> Res.string.sync_diagnostic_reason_offline
        DiagnosticReason.SIGN_IN_REQUIRED -> Res.string.sync_diagnostic_reason_sign_in_required
        DiagnosticReason.SERVER_UNAVAILABLE -> Res.string.sync_diagnostic_reason_server_unavailable
        DiagnosticReason.RATE_LIMITED -> Res.string.sync_diagnostic_reason_rate_limited
        DiagnosticReason.QUOTA_EXCEEDED -> Res.string.sync_diagnostic_reason_quota_exceeded
        DiagnosticReason.LOCAL_STORAGE -> Res.string.sync_diagnostic_reason_local_storage
        DiagnosticReason.MISSING_MEDIA -> Res.string.sync_diagnostic_reason_missing_media
        DiagnosticReason.CORRUPT_PAYLOAD -> Res.string.sync_diagnostic_reason_corrupt_payload
        DiagnosticReason.KEY_RECOVERY_REQUIRED -> Res.string.sync_diagnostic_reason_key_recovery_required
        DiagnosticReason.INCOMPATIBLE_SERVER -> Res.string.sync_diagnostic_reason_incompatible_server
        DiagnosticReason.PERSISTENCE_FAILED -> Res.string.sync_diagnostic_reason_persistence_failed
        DiagnosticReason.UNSUPPORTED_FORMAT -> Res.string.sync_diagnostic_reason_unsupported_format
        DiagnosticReason.CONFLICT -> Res.string.sync_diagnostic_reason_conflict
        DiagnosticReason.UNKNOWN -> Res.string.sync_diagnostic_reason_unknown
    }

internal fun DiagnosticRetryCondition.retryResource(): StringResource =
    when (this) {
        DiagnosticRetryCondition.UNKNOWN -> Res.string.sync_diagnostic_retry_unknown
        DiagnosticRetryCondition.AUTOMATIC -> Res.string.sync_diagnostic_retry_automatic
        DiagnosticRetryCondition.NETWORK -> Res.string.sync_diagnostic_retry_network
        DiagnosticRetryCondition.SIGN_IN -> Res.string.sync_diagnostic_retry_sign_in
        DiagnosticRetryCondition.KEY_RECOVERY -> Res.string.sync_diagnostic_retry_key_recovery
        DiagnosticRetryCondition.FREE_SPACE -> Res.string.sync_diagnostic_retry_free_space
        DiagnosticRetryCondition.UPDATE_APP -> Res.string.sync_diagnostic_retry_update_app
        DiagnosticRetryCondition.UPDATE_SERVER -> Res.string.sync_diagnostic_retry_update_server
        DiagnosticRetryCondition.REVIEW_CONFLICT -> Res.string.sync_diagnostic_retry_review_conflict
        DiagnosticRetryCondition.USER_RETRY -> Res.string.sync_diagnostic_retry_user_retry
    }
