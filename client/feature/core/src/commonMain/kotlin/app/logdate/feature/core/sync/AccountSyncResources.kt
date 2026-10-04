package app.logdate.feature.core.sync

import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.sync_account_disabled
import logdate.client.feature.core.generated.resources.sync_account_signed_out
import logdate.client.feature.core.generated.resources.sync_account_waiting
import logdate.client.feature.core.generated.resources.sync_feedback_up_to_date
import logdate.client.feature.core.generated.resources.syncing
import org.jetbrains.compose.resources.StringResource

internal fun AccountSyncStatus.messageResource(): StringResource =
    when (this) {
        AccountSyncStatus.UP_TO_DATE -> Res.string.sync_feedback_up_to_date
        AccountSyncStatus.SYNCING -> Res.string.syncing
        AccountSyncStatus.WAITING -> Res.string.sync_account_waiting
        AccountSyncStatus.OFFLINE -> Res.string.sync_account_waiting
        AccountSyncStatus.SERVER_UNAVAILABLE -> Res.string.sync_account_waiting
        AccountSyncStatus.CONNECTION_UNAVAILABLE -> Res.string.sync_account_waiting
        AccountSyncStatus.SIGN_IN_REQUIRED -> Res.string.sync_account_signed_out
        AccountSyncStatus.STORAGE_FULL -> Res.string.sync_account_waiting
        AccountSyncStatus.WAITING_FOR_WIFI -> Res.string.sync_account_waiting
        AccountSyncStatus.BACKGROUND_RESTRICTED -> Res.string.sync_account_waiting
        AccountSyncStatus.DEVICE_ACCESS_REQUIRED -> Res.string.sync_account_waiting
        AccountSyncStatus.CONFLICT -> Res.string.sync_account_waiting
        AccountSyncStatus.LOCAL_DATA_UNAVAILABLE -> Res.string.sync_account_waiting
        AccountSyncStatus.MEDIA_TOO_LARGE -> Res.string.sync_account_waiting
        AccountSyncStatus.UNKNOWN -> Res.string.sync_account_waiting
        AccountSyncStatus.DISABLED -> Res.string.sync_account_disabled
    }
