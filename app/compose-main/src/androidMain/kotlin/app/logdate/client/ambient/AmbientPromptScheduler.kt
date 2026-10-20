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
import androidx.work.await
import androidx.work.workDataOf
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.datastore.featureflags.FeatureFlagStore
import app.logdate.client.domain.recommendation.AmbientPromptTime
import app.logdate.client.domain.recommendation.AmbientPromptTriggerContext
import app.logdate.client.domain.recommendation.MemoriesSettings
import app.logdate.client.domain.recommendation.MemoriesSettingsRepository
import app.logdate.client.domain.watch.SharedPreferenceKey
import app.logdate.client.domain.watch.WatchPreferencesRepository
import app.logdate.client.reminders.MomentReminderWorker
import io.github.aakira.napier.Napier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AmbientPromptScheduler(
    private val context: Context,
    private val memoriesSettingsRepository: MemoriesSettingsRepository,
    private val flags: FeatureFlagStore,
    private val shared: WatchPreferencesRepository,
) {
    private val scheduling = Mutex()
    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val workManager = WorkManager.getInstance(context)

    suspend fun refreshSchedules() =
        scheduling.withLock {
            val settings = memoriesSettingsRepository.getSettings()
            if (flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)) {
                cancelFixedAlarms()
                if (shared.get(SharedPreferenceKey.RemindersEnabled)) {
                    enqueuePeriodicEvaluation(15, TimeUnit.MINUTES)
                } else {
                    cancelAll()
                }
                return@withLock
            }
            if (!settings.contextualRecommendationsEnabled || !settings.ambientPromptsEnabled || !hasAnyAmbientPromptFamily(settings)) {
                cancelAll()
                return@withLock
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

            enqueuePeriodicEvaluation(6, TimeUnit.HOURS)
        }

    private suspend fun enqueuePeriodicEvaluation(
        interval: Long,
        unit: TimeUnit,
    ) {
        val request =
            PeriodicWorkRequestBuilder<AmbientPromptWorker>(interval, unit)
                .setInputData(workDataOf(AmbientPromptWorker.KEY_TRIGGER_CONTEXT to AmbientPromptTriggerContext.PERIODIC.name))
                .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request).await()
    }

    suspend fun enqueueImmediateEvaluation(triggerContext: AmbientPromptTriggerContext) =
        scheduling.withLock {
            if (!isEvaluationEnabled(triggerContext)) return@withLock
            val request =
                OneTimeWorkRequestBuilder<AmbientPromptWorker>()
                    .setInputData(workDataOf(AmbientPromptWorker.KEY_TRIGGER_CONTEXT to triggerContext.name))
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

            workManager
                .enqueueUniqueWork(
                    "${IMMEDIATE_WORK_NAME}:${triggerContext.name.lowercase()}",
                    ExistingWorkPolicy.KEEP,
                    request,
                ).await()
        }

    suspend fun enqueueMomentEvaluation() =
        scheduling.withLock {
            if (!flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2) || !shared.get(SharedPreferenceKey.RemindersEnabled)) return@withLock
            workManager
                .enqueueUniqueWork(
                    SIGNAL_WORK_NAME,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    OneTimeWorkRequestBuilder<MomentReminderWorker>().build(),
                ).await()
        }

    private fun cancelFixedAlarms() {
        alarmManager.cancel(pendingIntent(REQUEST_MORNING, ACTION_MORNING_PROMPT))
        alarmManager.cancel(pendingIntent(REQUEST_EVENING, ACTION_EVENING_PROMPT))
    }

    private suspend fun cancelAll() {
        cancelFixedAlarms()
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME).await()
        workManager.cancelUniqueWork(SIGNAL_WORK_NAME).await()
        AmbientPromptTriggerContext.entries.forEach { trigger ->
            workManager.cancelUniqueWork("${IMMEDIATE_WORK_NAME}:${trigger.name.lowercase()}").await()
        }
    }

    private suspend fun isEvaluationEnabled(trigger: AmbientPromptTriggerContext): Boolean {
        if (flags.isEnabled(FeatureFlag.WATCH_EXPERIENCE_V2)) {
            return trigger == AmbientPromptTriggerContext.PERIODIC && shared.get(SharedPreferenceKey.RemindersEnabled)
        }
        val settings = memoriesSettingsRepository.getSettings()
        if (!settings.contextualRecommendationsEnabled ||
            !settings.ambientPromptsEnabled ||
            !hasAnyAmbientPromptFamily(settings)
        ) {
            return false
        }
        return when (trigger) {
            AmbientPromptTriggerContext.PERIODIC -> true
            AmbientPromptTriggerContext.MORNING_SCHEDULE -> settings.captureNudgesEnabled && settings.morningPromptEnabled
            AmbientPromptTriggerContext.EVENING_SCHEDULE -> settings.captureNudgesEnabled && settings.eveningPromptEnabled
        }
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
        private const val SIGNAL_WORK_NAME = "logdate:moment-reminder:signal"
        private const val PERIODIC_WORK_NAME = "logdate:ambient:periodic"
        private const val IMMEDIATE_WORK_NAME = "logdate:ambient:immediate"
        const val ACTION_MORNING_PROMPT = "app.logdate.client.ambient.action.MORNING_PROMPT"
        const val ACTION_EVENING_PROMPT = "app.logdate.client.ambient.action.EVENING_PROMPT"
        private const val REQUEST_MORNING = 4101
        private const val REQUEST_EVENING = 4102
    }
}
