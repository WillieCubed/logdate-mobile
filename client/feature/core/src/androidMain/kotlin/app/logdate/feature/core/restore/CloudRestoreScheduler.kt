package app.logdate.feature.core.restore

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.logdate.client.datastore.OriginBoundSession
import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.restore.PostRestoreDetector
import io.github.aakira.napier.Napier
import java.security.MessageDigest

/** Schedules an authenticated cloud restore without affecting local/offline data. */
class CloudRestoreScheduler(
    private val context: Context,
    private val sessions: SessionStorage,
) {
    private val workManager = WorkManager.getInstance(context)

    fun enqueueRestore() {
        val delivery = PostRestoreDetector(context).pendingContentRecoveryId()
        val session = sessions.getOriginBoundSession()
        if (delivery != null && session == null) return
        val data = NativeRestoreWorkState.bind(Data.Builder(), delivery, session).build()
        val request =
            OneTimeWorkRequestBuilder<CloudRestoreWorker>()
                .setInputData(data)
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                ).addTag(CloudRestoreWorker.WORK_TAG)
                .build()
        workManager.enqueueUniqueWork(
            workName(session, delivery),
            ExistingWorkPolicy.KEEP,
            request,
        )
        Napier.d("Enqueued LogDate Cloud restore")
    }

    companion object {
        fun workName(
            session: OriginBoundSession?,
            deliveryId: String? = null,
        ): String = "${CloudRestoreWorker.WORK_NAME}:${scopeDigest(session, deliveryId)}"

        fun deliveryWorkTag(
            deliveryId: String,
            session: OriginBoundSession,
        ): String = "${CloudRestoreWorker.WORK_TAG}:delivery:${scopeDigest(session, deliveryId)}"

        private fun scopeDigest(
            session: OriginBoundSession?,
            deliveryId: String?,
        ): String {
            val scope = "${session?.let { "${it.origin}\n${it.session.accountId}" } ?: "no-session"}\n${deliveryId.orEmpty()}"
            val digest = MessageDigest.getInstance("SHA-256").digest(scope.encodeToByteArray())
            return digest.joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
        }
    }
}
