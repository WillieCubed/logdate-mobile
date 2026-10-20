package app.logdate.client.sync

import androidx.work.Data
import app.logdate.client.sync.metadata.UploadScope

private const val RECOVERED_OWNER = "recovered_media_owner"
private const val RECOVERED_ORIGIN = "recovered_media_origin"

/** Only a successful full run can prove that both entry and draft media were recovered. */
internal fun mediaRecoveryCompletionOutput(
    syncType: String,
    scope: UploadScope?,
): Data =
    Data
        .Builder()
        .apply {
            if (syncType == AndroidLogDateSyncWorker.SYNC_TYPE_FULL && scope != null) {
                putString(RECOVERED_OWNER, scope.ownerId)
                putString(RECOVERED_ORIGIN, scope.serverOrigin)
            }
        }.build()

internal fun completedMediaRecoveryScope(output: Data): UploadScope? {
    val owner = output.getString(RECOVERED_OWNER) ?: return null
    val origin = output.getString(RECOVERED_ORIGIN) ?: return null
    return UploadScope(owner, origin)
}
