package app.logdate.client.rewind

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.logdate.client.datastore.LogdatePreferencesDataSource
import app.logdate.client.domain.rewind.GenerateBasicRewindResult
import app.logdate.client.domain.rewind.GenerateBasicRewindUseCase
import app.logdate.client.repository.rewind.RewindRepository
import app.logdate.util.getLocaleFirstDayOfWeek
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Background worker that generates weekly rewinds when they're not yet available.
 *
 * Computes the bounds of the previous complete week, checks the repository, and
 * triggers generation directly if no rewind exists yet. On success, posts a
 * "rewind ready" notification with a deep link to the detail screen.
 */
class RewindGenerationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val rewindRepository: RewindRepository by inject()
    private val generateRewind: GenerateBasicRewindUseCase by inject()
    private val preferences: LogdatePreferencesDataSource by inject()
    private val notificationCoordinator: RewindNotificationCoordinator by inject()

    override suspend fun doWork(): Result {
        Napier.d("RewindGenerationWorker: checking if weekly rewind needs generation")

        val (start, end) = lastWeekBounds()

        // If a rewind already exists for last week, nothing to do.
        rewindRepository.getRewindBetween(start, end).firstOrNull()?.let {
            Napier.d("RewindGenerationWorker: weekly rewind already exists")
            return Result.success()
        }

        return when (val result = generateRewind(start, end)) {
            is GenerateBasicRewindResult.Success -> {
                Napier.d("RewindGenerationWorker: generated weekly rewind ${result.rewind.uid}")
                notificationCoordinator.postRewindReady(result.rewind)
                Result.success()
            }
            is GenerateBasicRewindResult.AlreadyInProgress -> {
                Napier.d("RewindGenerationWorker: generation already in progress")
                Result.success()
            }
            is GenerateBasicRewindResult.NoContent -> {
                Napier.d("RewindGenerationWorker: no content for last week, skipping")
                Result.success()
            }
            is GenerateBasicRewindResult.Error -> {
                Napier.w("RewindGenerationWorker: generation failed — ${result.error}")
                Result.retry()
            }
        }
    }

    private suspend fun lastWeekBounds(): Pair<Instant, Instant> {
        val timezone = TimeZone.currentSystemDefault()
        val today = Clock.System.todayIn(timezone)

        val weekStart = preferences.getFirstDayOfWeek() ?: getLocaleFirstDayOfWeek()
        val daysFromWeekStart = (today.dayOfWeek.ordinal - weekStart.ordinal + 7) % 7
        val startOfThisWeek = today.minus(daysFromWeekStart, DateTimeUnit.DAY)
        val startOfLastWeek = startOfThisWeek.minus(7, DateTimeUnit.DAY)

        return startOfLastWeek.atStartOfDayIn(timezone) to startOfThisWeek.atStartOfDayIn(timezone)
    }

    companion object {
        const val WORK_NAME = "logdate:rewind:weekly_generation"
    }
}
