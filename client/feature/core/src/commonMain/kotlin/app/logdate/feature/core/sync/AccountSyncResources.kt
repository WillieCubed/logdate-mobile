package app.logdate.feature.core.sync

import logdate.client.feature.core.generated.resources.Res
import logdate.client.feature.core.generated.resources.sync_account_background
import logdate.client.feature.core.generated.resources.sync_account_checking
import logdate.client.feature.core.generated.resources.sync_account_disabled
import logdate.client.feature.core.generated.resources.sync_account_offline
import logdate.client.feature.core.generated.resources.sync_account_retrying
import logdate.client.feature.core.generated.resources.sync_account_signed_out
import logdate.client.feature.core.generated.resources.sync_account_storage_full
import logdate.client.feature.core.generated.resources.sync_account_unknown
import logdate.client.feature.core.generated.resources.sync_account_waiting
import logdate.client.feature.core.generated.resources.sync_account_wifi
import logdate.client.feature.core.generated.resources.sync_feedback_up_to_date
import logdate.client.feature.core.generated.resources.syncing
import org.jetbrains.compose.resources.StringResource

internal fun AccountSyncStatus.messageResource(): StringResource =
    when (this) {
        AccountSyncStatus.RETRYING -> Res.string.sync_account_retrying
        AccountSyncStatus.CHECKING -> Res.string.sync_account_checking
        AccountSyncStatus.UP_TO_DATE -> Res.string.sync_feedback_up_to_date
        AccountSyncStatus.SYNCING -> Res.string.syncing
        AccountSyncStatus.WAITING -> Res.string.sync_account_waiting
        AccountSyncStatus.OFFLINE -> Res.string.sync_account_offline
        AccountSyncStatus.SERVER_UNAVAILABLE -> Res.string.sync_account_unknown
        AccountSyncStatus.CONNECTION_UNAVAILABLE -> Res.string.sync_account_unknown
        AccountSyncStatus.SIGN_IN_REQUIRED -> Res.string.sync_account_signed_out
        AccountSyncStatus.STORAGE_FULL -> Res.string.sync_account_storage_full
        AccountSyncStatus.WAITING_FOR_WIFI -> Res.string.sync_account_wifi
        AccountSyncStatus.BACKGROUND_RESTRICTED -> Res.string.sync_account_background
        AccountSyncStatus.DEVICE_ACCESS_REQUIRED -> Res.string.sync_account_unknown
        AccountSyncStatus.CONFLICT -> Res.string.sync_account_unknown
        AccountSyncStatus.LOCAL_DATA_UNAVAILABLE -> Res.string.sync_account_unknown
        AccountSyncStatus.MEDIA_TOO_LARGE -> Res.string.sync_account_unknown
        AccountSyncStatus.UNKNOWN -> Res.string.sync_account_unknown
        AccountSyncStatus.DISABLED -> Res.string.sync_account_disabled
    }
