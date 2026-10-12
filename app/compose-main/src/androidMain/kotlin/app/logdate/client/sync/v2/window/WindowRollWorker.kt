package app.logdate.client.sync.v2.window

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/** Rolls the library forward even when the phone app has closed. */
class WindowRollWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters),
    KoinComponent {
    private val publisher: WatchWindowPublisher by inject()

    override suspend fun doWork(): Result =
        try {
            // Publishing schedules its successor last; replacing this work must not interrupt a write.
            withContext(NonCancellable) { publisher.publishAll() }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.w("Could not roll the watch library forward; background work will retry", e)
            Result.retry()
        }
}

class WorkManagerWindowRollWork(
    private val manager: WorkManager,
) : WindowRollWork {
    override fun runAfter(delayMs: Long) {
        val request =
            OneTimeWorkRequestBuilder<WindowRollWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
        manager.enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        manager.cancelUniqueWork(NAME)
    }

    companion object {
        const val NAME = "watch_library_roll"
    }
}
