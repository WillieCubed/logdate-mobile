package app.logdate.wear

import android.app.Application
import app.logdate.client.data.notes.StoredMediaReferenceMigrationLauncher
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.datastore.LogDateConfigDataSource
import app.logdate.client.datastore.featureflags.FeatureFlag
import app.logdate.client.media.audio.RecordingNotificationDecorator
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.notifications.LogDateNotificationRegistrar
import app.logdate.wear.di.wearAudioModule
import app.logdate.wear.di.wearDataModule
import app.logdate.wear.di.wearExperienceModule
import app.logdate.wear.di.wearIdentityModule
import app.logdate.wear.di.wearSyncV2Module
import app.logdate.wear.di.wearV2Module
import app.logdate.wear.launch.FeatureSwitch
import app.logdate.wear.recording.OngoingRecordingDecorator
import app.logdate.wear.recording.WearRecordingRecovery
import app.logdate.wear.reminders.LegacyPromptAlarms
import app.logdate.wear.reminders.WatchReminders
import app.logdate.wear.reminders.applyReminderBridging
import app.logdate.wear.sync.WatchSyncScheduler
import app.logdate.wear.sync.WearDeadLetterMigration
import app.logdate.wear.sync.v2.WatchStateMirror
import app.logdate.wear.sync.v2.link.WatchLinkCoordinator
import app.logdate.wear.v2.surfaces.SurfaceCoordinator
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import kotlin.concurrent.thread

/**
 * Application class for LogDate Wear OS app.
 *
 * Initializes:
 * - Koin dependency injection
 * - Napier logging
 * - Audio recording modules
 *
 * It also supplies the recording notification's decorator, which shows a recording on the watch
 * face as an Ongoing Activity while the new watch app is on.
 */
class LogDateWearApplication :
    Application(),
    RecordingNotificationDecorator.Provider {
    /** WATCH_EXPERIENCE_V2 for code that can't suspend, such as the recording notification. */
    internal val newWatchApp: FeatureSwitch by lazy {
        FeatureSwitch(
            featureFlags =
                org.koin.java.KoinJavaComponent
                    .getKoin()
                    .get(),
            flag = FeatureFlag.WATCH_EXPERIENCE_V2,
        )
    }

    override val recordingNotificationDecorator: RecordingNotificationDecorator by lazy {
        OngoingRecordingDecorator(enabled = { newWatchApp.isOn })
    }

    override fun onCreate() {
        super.onCreate()
        val launchedAtMs = System.currentTimeMillis()

        Napier.base(DebugAntilog())

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@LogDateWearApplication)
            modules(
                wearDataModule,
                wearIdentityModule,
                wearAudioModule,
                wearExperienceModule,
                wearV2Module,
                wearSyncV2Module,
            )
        }

        listOf(LogDateNotificationChannelKey.AUDIO_PLAYBACK, LogDateNotificationChannelKey.AUDIO_RECORDING).forEach { channel ->
            runCatching {
                LogDateNotificationRegistrar(this).registerChannel(channel)
            }.onFailure { error ->
                Napier.w("Failed to register Wear notification channel ${channel.id} on app startup", error)
            }
        }

        recoverInterruptedRecordings(launchedAtMs)
        newWatchApp.start(
            org.koin.java.KoinJavaComponent
                .getKoin()
                .get<CoroutineScope>(),
        )
        startSync()
        startSurfaces()

        runCatching { LegacyPromptAlarms.cancel(this) }
            .onFailure { error -> Napier.w("Failed to remove the legacy journal prompts", error) }
        applyReminderBridging(this)

        // Trigger database initialization on a background thread.
        // The database singleton uses runBlocking for SQLCipher passphrase retrieval
        // and Room database opening. By resolving it here off the main thread, the
        // heavy I/O (KeyStore, EncryptedSharedPreferences, SQLCipher, Room migrations)
        // runs in parallel with Activity creation instead of blocking the first UI frame.
        warmUpDatabase()
    }

    /**
     * Saves recordings a killed process left behind. Only files written before this launch are
     * touched, so a recording started right away is never mistaken for one of them.
     */
    private fun recoverInterruptedRecordings(launchedAtMs: Long) {
        thread(name = "recording-recovery", isDaemon = true) {
            try {
                val recovery =
                    org.koin.java.KoinJavaComponent
                        .getKoin()
                        .get<WearRecordingRecovery>()
                val recovered = runBlocking { recovery.recover(modifiedBefore = launchedAtMs) }
                if (recovered > 0) Napier.i("Recovered $recovered interrupted recording(s)")
            } catch (e: Exception) {
                Napier.e("Recovering interrupted recordings failed", e)
            }
        }
    }

    /**
     * Puts captures an older version dropped after repeated failures back in the outbox, wakes the
     * watch whenever something in it is due, and follows the phone's state.
     */
    private fun startSync() {
        val koin =
            org.koin.java.KoinJavaComponent
                .getKoin()
        val scope = koin.get<CoroutineScope>()
        koin.get<WatchStateMirror>().start(scope)
        scope.launch { resumeAccountLink(koin) }
        scope.launch {
            newWatchApp.state.collect { restoreReminders(koin.get()) }
        }
        scope.launch {
            requeueSetAsideCaptures(koin.get())
            koin.get<WatchSyncScheduler>().start(this)
        }
    }

    /** Loads the saved server first, so a link finished now is not undone by the saved one arriving later. */
    private suspend fun resumeAccountLink(koin: org.koin.core.Koin) {
        try {
            koin.get<LogDateConfigDataSource>().awaitConfigurationLoaded()
            koin.get<WatchLinkCoordinator>().resumeInstall()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Finishing the phone's account link failed; the phone links this watch again", e)
        }
    }

    private suspend fun requeueSetAsideCaptures(migration: WearDeadLetterMigration) {
        try {
            val requeued = migration.migrate()
            if (requeued > 0) Napier.i("Queued $requeued capture(s) again that an older version set aside")
        } catch (e: Exception) {
            Napier.e("Queueing set-aside captures again failed", e)
        }
    }

    /** Listens for workouts again after an update or a cleared Health Services registration, while reminders are on. */
    private suspend fun restoreReminders(reminders: WatchReminders) {
        try {
            reminders.restore()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Napier.e("Restoring reminders at start failed", e)
        }
    }

    private fun startSurfaces() {
        val koin =
            org.koin.java.KoinJavaComponent
                .getKoin()
        koin.get<SurfaceCoordinator>().start(koin.get<CoroutineScope>())
    }

    private fun warmUpDatabase() {
        thread(name = "db-warmup", isDaemon = true) {
            try {
                val koin =
                    org.koin.java.KoinJavaComponent
                        .getKoin()
                koin.get<LogDateDatabase>()
                koin.get<StoredMediaReferenceMigrationLauncher>().start()
            } catch (e: Exception) {
                Napier.w("Background database warmup failed", e)
            }
        }
    }
}
