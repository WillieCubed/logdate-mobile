package app.logdate.wear

import android.app.Application
import app.logdate.client.database.LogDateDatabase
import app.logdate.client.notifications.LogDateNotificationChannelKey
import app.logdate.client.notifications.LogDateNotificationRegistrar
import app.logdate.wear.di.wearAudioModule
import app.logdate.wear.di.wearDataModule
import app.logdate.wear.notification.WearPromptScheduler
import app.logdate.wear.recording.WearRecordingRecovery
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
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
 */
class LogDateWearApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val launchedAtMs = System.currentTimeMillis()

        Napier.base(DebugAntilog())

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@LogDateWearApplication)
            modules(
                wearDataModule,
                wearAudioModule,
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

        // Schedule morning/evening journal prompt alarms.
        WearPromptScheduler(this).scheduleAll()

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
                val recovery = org.koin.java.KoinJavaComponent.getKoin().get<WearRecordingRecovery>()
                val recovered = runBlocking { recovery.recover(modifiedBefore = launchedAtMs) }
                if (recovered > 0) Napier.i("Recovered $recovered interrupted recording(s)")
            } catch (e: Exception) {
                Napier.e("Recovering interrupted recordings failed", e)
            }
        }
    }

    private fun warmUpDatabase() {
        thread(name = "db-warmup", isDaemon = true) {
            try {
                org.koin.java.KoinJavaComponent
                    .getKoin()
                    .get<LogDateDatabase>()
            } catch (e: Exception) {
                Napier.w("Background database warmup failed", e)
            }
        }
    }
}
