package app.logdate.wear.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.logdate.client.domain.today.TodaySummaryCalculator
import app.logdate.client.repository.journals.JournalNotesRepository
import app.logdate.wear.sync.PhoneReachability
import app.logdate.wear.v2.today.DayClock
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The next local evening, including changes to daylight saving time. */
fun nextEveningCheck(
    now: Instant,
    timeZone: TimeZone,
): Instant {
    val today = now.toLocalDateTime(timeZone).date
    val tonight = LocalDateTime(today, WatchReminderRules.EVENING_CHECK).toInstant(timeZone)
    if (now < tonight) return tonight
    return LocalDateTime(today.plus(1, DateTimeUnit.DAY), WatchReminderRules.EVENING_CHECK).toInstant(timeZone)
}

interface EveningReminderWork {
    /** Append from a running worker so scheduling tomorrow does not cancel today's run. */
    fun runAfter(
        delayMs: Long,
        replace: Boolean = true,
    )

    fun cancel()
}

class WorkManagerEveningReminderWork(
    private val workManager: WorkManager,
) : EveningReminderWork {
    override fun runAfter(
        delayMs: Long,
        replace: Boolean,
    ) {
        val request =
            OneTimeWorkRequestBuilder<EveningReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
        val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE
        workManager.enqueueUniqueWork(EveningReminderWorker.UNIQUE_WORK_NAME, policy, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(EveningReminderWorker.UNIQUE_WORK_NAME)
    }
}

class EveningReminderScheduler(
    private val work: EveningReminderWork,
    private val clock: Clock = Clock.System,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    fun scheduleNext(replace: Boolean = true) {
        val now = clock.now()
        work.runAfter((nextEveningCheck(now, timeZone()) - now).inWholeMilliseconds, replace)
    }

    fun cancel() = work.cancel()
}

/** Rechecks consent after suspended reads, immediately before posting. */
class EveningReminderCheck(
    private val active: () -> Boolean,
    private val phoneReachability: PhoneReachability,
    private val momentsToday: suspend () -> Int,
    private val dispatcher: WatchReminderDispatcher,
) {
    suspend fun run(): Boolean {
        if (!active()) return false
        val reachable = withTimeoutOrNull(5.seconds) { phoneReachability.observe().first() } != null
        if (reachable) return false
        val count = withTimeout(10.seconds) { momentsToday() }
        return active() && dispatcher.onEveningCheck(phoneReachable = false, momentsToday = count)
    }
}

/** A delayed check may run late; the dispatch rules still enforce evening and waking hours. */
class EveningReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val reminders: WatchReminders by inject()
    private val dispatcher: WatchReminderDispatcher by inject()
    private val scheduler: EveningReminderScheduler by inject()
    private val phoneReachability: PhoneReachability by inject()
    private val dayClock: DayClock by inject()
    private val notesRepository: JournalNotesRepository by inject()

    override suspend fun doWork(): Result {
        if (!reminders.isActive) return Result.success()
        return try {
            EveningReminderCheck(
                active = { reminders.isActive },
                phoneReachability = phoneReachability,
                momentsToday = {
                    val today = dayClock.current()
                    val notes =
                        notesRepository
                            .observeNotesInRange(today.start, today.end)
                            .first()
                            .filter { it.creationTimestamp in today }
                    TodaySummaryCalculator.calculate(notes).momentCount
                },
                dispatcher = dispatcher,
            ).run()
            if (reminders.isActive) scheduler.scheduleNext(replace = false)
            Result.success()
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            Napier.w("The evening reminder check timed out", e)
            Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("The evening reminder check failed", e)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "evening_reminder"
    }
}
