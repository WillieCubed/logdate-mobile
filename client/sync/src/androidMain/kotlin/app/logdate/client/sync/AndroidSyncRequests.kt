package app.logdate.client.sync

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import app.logdate.client.sync.metadata.UploadScope

internal fun immediateSyncRequest(
    syncType: String,
    consent: UploadScope?,
): OneTimeWorkRequest {
    val constraints =
        Constraints
            .Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    val inputData =
        Data
            .Builder()
            .putAll(mobileDataSyncInput(consent))
            .putString(AndroidLogDateSyncWorker.KEY_SYNC_TYPE, syncType)
            .build()

    val request =
        OneTimeWorkRequestBuilder<AndroidLogDateSyncWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .apply { if (consent != null) addTag(MOBILE_DATA_SYNC_TAG) }
            // Out of expedited quota, run it as ordinary work rather than drop it. This is what
            // "Back up now" schedules, and dropping it threw the request away with nothing on
            // screen changing: the backup waited for the next periodic run, hours later, while
            // the app showed items waiting and a button that seemed to do nothing.
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

    return request
}
