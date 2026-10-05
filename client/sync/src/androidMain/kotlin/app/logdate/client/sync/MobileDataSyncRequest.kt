package app.logdate.client.sync

import androidx.work.Data
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.sync.metadata.UploadScope

internal const val MOBILE_DATA_SYNC_TAG = "mobile_data_sync"

private const val KEY_MOBILE_DATA_OWNER = "mobile_data_owner"
private const val KEY_MOBILE_DATA_ORIGIN = "mobile_data_origin"

internal fun mobileDataSyncInput(scope: UploadScope?): Data =
    Data
        .Builder()
        .apply {
            if (scope != null) {
                putString(KEY_MOBILE_DATA_OWNER, scope.ownerId)
                putString(KEY_MOBILE_DATA_ORIGIN, scope.serverOrigin)
            }
        }.build()

internal fun mobileDataSyncScope(
    input: Data,
    current: OriginBoundSession?,
): UploadScope? {
    if (current == null) return null
    val owner = input.getString(KEY_MOBILE_DATA_OWNER) ?: return null
    val origin = input.getString(KEY_MOBILE_DATA_ORIGIN) ?: return null
    return UploadScope(owner, origin).takeIf { owner == current.session.accountId && origin == current.origin }
}
