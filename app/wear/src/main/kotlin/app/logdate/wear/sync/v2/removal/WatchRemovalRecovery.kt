package app.logdate.wear.sync.v2.removal

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.logdate.wear.recording.WearRecorder
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WatchRemovalRecovery(
    private val context: Context,
    private val lock: WatchRemovalLock,
    private val recorder: WearRecorder,
    private val wiper: WatchDataWiper,
    private val pending: suspend () -> Int,
) {
    suspend fun restore() {
        val state = lock.state()
        if (state == "wiping" || state?.startsWith("wiping:") == true) schedule()
    }

    suspend fun finish(): Boolean {
        val state = lock.state() ?: return true
        if (state != "wiping" && !state.startsWith("wiping:")) return true
        if (recorder.isRecording.value || (state.startsWith("wiping:") && pending() != 0)) return false
        wiper.wipe()
        return true
    }

    private fun schedule() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "watch_secure_removal",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WatchRemovalWorker>().build(),
        )
    }
}

class WatchRemovalWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters),
    KoinComponent {
    private val recovery: WatchRemovalRecovery by inject()

    override suspend fun doWork(): Result =
        try {
            if (recovery.finish()) Result.success() else Result.retry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Napier.e("Watch erasure is incomplete and will resume", error)
            Result.retry()
        }
}
