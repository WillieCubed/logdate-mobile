package app.logdate.client.shortcuts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.logdate.client.feature.widgets.shortcuts.DynamicShortcutPublisher
import io.github.aakira.napier.Napier
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Background worker that recomputes the current dynamic shortcut set and applies
 * it via [AndroidDynamicShortcutApplier].
 *
 * Scheduled by [DynamicShortcutScheduler] on app startup and once a day to pick
 * up day-rollover and weekly-rewind state changes. Uses retry on failure rather
 * than failure so transient errors (e.g. database recovery in progress) get a
 * second chance.
 */
class DynamicShortcutRefreshWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams),
    KoinComponent {
    private val publisher: DynamicShortcutPublisher by inject()
    private val applier: AndroidDynamicShortcutApplier by inject()

    override suspend fun doWork(): Result =
        try {
            val descriptors = publisher.computeShortcuts()
            applier.apply(descriptors)
            Napier.d("Dynamic shortcuts published: ${descriptors.size} entries")
            Result.success()
        } catch (error: Exception) {
            Napier.w("Failed to publish dynamic shortcuts", error)
            Result.retry()
        }
}
