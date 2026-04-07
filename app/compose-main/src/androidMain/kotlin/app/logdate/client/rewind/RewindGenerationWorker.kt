package app.logdate.client.rewind

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.logdate.client.domain.rewind.GenerateBasicRewindUseCase
import app.logdate.client.domain.rewind.GetWeekRewindUseCase
import app.logdate.client.domain.rewind.RewindQueryResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.firstOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Background worker that generates weekly rewinds when they're not yet available.
 *
 * This worker checks if last week's rewind has been generated and, if not,
 * triggers generation in the background. It runs periodically via WorkManager
 * and can also be triggered on-demand at app startup.
 */
class RewindGenerationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val getWeekRewind: GetWeekRewindUseCase by inject()
    private val generateRewind: GenerateBasicRewindUseCase by inject()

    override suspend fun doWork(): Result {
        Napier.d("RewindGenerationWorker: checking if weekly rewind needs generation")

        // Check current status of last week's rewind
        val weekResult = getWeekRewind().firstOrNull()

        return when (weekResult) {
            is RewindQueryResult.Success -> {
                Napier.d("RewindGenerationWorker: weekly rewind already exists")
                Result.success()
            }
            is RewindQueryResult.Generating -> {
                Napier.d("RewindGenerationWorker: generation already in progress")
                Result.success()
            }
            is RewindQueryResult.NotReady -> {
                Napier.d("RewindGenerationWorker: week not yet complete, skipping")
                Result.success()
            }
            is RewindQueryResult.NoneAvailable, null -> {
                Napier.d("RewindGenerationWorker: no rewind available, triggering generation")
                triggerGeneration()
            }
        }
    }

    private suspend fun triggerGeneration(): Result {
        // GetWeekRewindUseCase already computes the correct week bounds,
        // but we need them to call GenerateBasicRewindUseCase directly.
        // The GetWeekRewindUseCase triggers generation internally when it finds NoneAvailable,
        // so we just need to wait for it to resolve.
        val result = getWeekRewind().firstOrNull()
        return when (result) {
            is RewindQueryResult.Success -> {
                Napier.d("RewindGenerationWorker: generation completed successfully")
                Result.success()
            }
            is RewindQueryResult.Generating -> {
                // Generation was triggered but hasn't completed yet — that's fine, it'll finish in the background
                Napier.d("RewindGenerationWorker: generation triggered, will complete asynchronously")
                Result.success()
            }
            else -> {
                Napier.w("RewindGenerationWorker: generation could not be started")
                Result.retry()
            }
        }
    }

    companion object {
        const val WORK_NAME = "logdate:rewind:weekly_generation"
    }
}
