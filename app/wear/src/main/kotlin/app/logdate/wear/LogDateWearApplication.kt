package app.logdate.wear

import android.app.Application
import app.logdate.client.database.LogDateDatabase
import app.logdate.wear.di.wearAudioModule
import app.logdate.wear.di.wearDataModule
import app.logdate.wear.notification.WearPromptScheduler
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
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

        Napier.base(DebugAntilog())

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@LogDateWearApplication)
            modules(
                wearDataModule,
                wearAudioModule,
            )
        }

        // Schedule morning/evening journal prompt alarms.
        WearPromptScheduler(this).scheduleAll()

        // Trigger database initialization on a background thread.
        // The database singleton uses runBlocking for SQLCipher passphrase retrieval
        // and Room database opening. By resolving it here off the main thread, the
        // heavy I/O (KeyStore, EncryptedSharedPreferences, SQLCipher, Room migrations)
        // runs in parallel with Activity creation instead of blocking the first UI frame.
        thread(name = "db-warmup", isDaemon = true) {
            try {
                org.koin.java.KoinJavaComponent.getKoin().get<LogDateDatabase>()
            } catch (e: Exception) {
                Napier.w("Background database warmup failed", e)
            }
        }
    }
}