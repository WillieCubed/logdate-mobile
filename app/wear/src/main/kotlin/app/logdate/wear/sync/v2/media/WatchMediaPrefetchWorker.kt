package app.logdate.wear.sync.v2.media

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.logdate.wear.launch.awaitWearStartup
import app.logdate.wear.playback.NoRoomForAudioException
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** WorkManager cancels this run when charging or unmetered connectivity is lost. */
class WatchMediaPrefetchWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters),
    KoinComponent {
    private val prefetcher: WatchMediaPrefetcher by inject()

    override suspend fun doWork(): Result {
        return try {
            if (!applicationContext.awaitWearStartup()) return Result.retry()
            withContext(Dispatchers.IO) { prefetcher.run() }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoRoomForAudioException) {
            Napier.i("Phone audio prefetch paused to preserve room for watch recordings")
            Result.success()
        } catch (e: Exception) {
            Napier.w("Phone audio prefetch could not finish", e)
            if (runAttemptCount >= MAX_RETRIES) Result.success() else Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "watch_verified_media_prefetch"
        private const val MAX_RETRIES = 2
    }
}
