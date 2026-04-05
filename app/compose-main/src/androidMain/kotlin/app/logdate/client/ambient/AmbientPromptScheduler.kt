package app.logdate.client.ambient

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.logdate.client.domain.recommendation.AmbientPromptTime
import app.logdate.client.domain.recommendation.AmbientPromptTriggerContext
import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import io.github.aakira.napier.Napier
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AmbientPromptScheduler(
    private val context: Context,
    private val memoriesSettingsRepository: MemoriesSettingsRepository,
) {
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val workManager = WorkManager.getInstance(context)

    suspend fun refreshSchedules() {
        refreshSchedules(memoriesSettingsRepository.getSettings())
    }

    fun refreshSchedules(settings: MemoriesSettings) {
        if (!settings.contextualRecommendationsEnabled || !settings.ambientPromptsEnabled || !hasAnyAmbientPromptFamily(settings)) {
            cancelAll()
            return
        }

        if (settings.captureNudgesEnabled && settings.morningPromptEnabled) {
            scheduleDailyAlarm(
                requestCode = REQUEST_MORNING,
                action = ACTION_MORNING_PROMPT,
                time = settings.morningPromptTime,
            )
        } else {
            alarmManager.cancel(pendingIntent(REQUEST_MORNING, ACTION_MORNING_PROMPT))
        }

        if (settings.captureNudgesEnabled && settings.eveningPromptEnabled) {
            scheduleDailyAlarm(
                requestCode = REQUEST_EVENING,
                action = ACTION_EVENING_PROMPT,
                time = settings.eveningPromptTime,
            )
        } else {
            alarmManager.cancel(pendingIntent(REQUEST_EVENING, ACTION_EVENING_PROMPT))
        }

        val periodicRequest =
            PeriodicWorkRequestBuilder<AmbientPromptWorker>(
                6,
                TimeUnit.HOURS,
            ).setInputData(
                workDataOf(AmbientPromptWorker.KEY_TRIGGER_CONTEXT to AmbientPromptTriggerContext.PERIODIC.name),
            ).build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest,
        )
    }

    fun enqueueImmediateEvaluation(triggerContext: AmbientPromptTriggerContext) {
        val request =
            OneTimeWorkRequestBuilder<AmbientPromptWorker>()
                .setInputData(workDataOf(AmbientPromptWorker.KEY_TRIGGER_CONTEXT to triggerContext.name))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

        workManager.enqueueUniqueWork(
            "${IMMEDIATE_WORK_NAME}:${triggerContext.name.lowercase()}",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun cancelAll() {
        alarmManager.cancel(pendingIntent(REQUEST_MORNING, ACTION_MORNING_PROMPT))
        alarmManager.cancel(pendingIntent(REQUEST_EVENING, ACTION_EVENING_PROMPT))
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    private fun hasAnyAmbientPromptFamily(settings: MemoriesSettings): Boolean =
        settings.captureNudgesEnabled ||
            settings.draftRescueEnabled ||
            settings.memoryRecallNotificationsEnabled

    private fun scheduleDailyAlarm(
        requestCode: Int,
        action: String,
        time: AmbientPromptTime,
    ) {
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            nextAlarmTime(time),
            AlarmManager.INTERVAL_DAY,
            pendingIntent(requestCode, action),
        )
        Napier.d("Scheduled ambient prompt alarm for $action at ${time.hour}:${time.minute}")
    }

    private fun nextAlarmTime(time: AmbientPromptTime): Long {
        val calendar =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, time.hour)
                set(Calendar.MINUTE, time.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        return calendar.timeInMillis
    }

    private fun pendingIntent(
        requestCode: Int,
        action: String,
    ): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AmbientPromptAlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val PERIODIC_WORK_NAME = "logdate:ambient:periodic"
        private const val IMMEDIATE_WORK_NAME = "logdate:ambient:immediate"
        const val ACTION_MORNING_PROMPT = "app.logdate.client.ambient.action.MORNING_PROMPT"
        const val ACTION_EVENING_PROMPT = "app.logdate.client.ambient.action.EVENING_PROMPT"
        private const val REQUEST_MORNING = 4101
        private const val REQUEST_EVENING = 4102
    }
}
