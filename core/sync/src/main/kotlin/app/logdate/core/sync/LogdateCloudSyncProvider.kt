package app.logdate.core.sync

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.logdate.core.data.user.UserStateRepository
import app.logdate.core.sync.workers.LogdateSyncWorker.Companion.SYNC_WORK_NAME
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * A backup and sync service for LogDate Cloud.
 */
class LogdateCloudSyncProvider @Inject constructor(
    private val context: Context,
    private val userDataRepository: UserStateRepository,
) : LogdateServiceSyncProvider {

    override val enabled: Boolean
        get() = TODO("Not yet implemented")

    override val isSyncing: Flow<Boolean>
        get() = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(SYNC_WORK_NAME)
            .map(List<WorkInfo>::anyRunning)
            .conflate()

    override fun sync(overwriteLocal: Boolean) {
        TODO("Not yet implemented")
    }
}

private fun List<WorkInfo>.anyRunning() = any { it.state == WorkInfo.State.RUNNING }