package app.logdate.wear

import android.app.Application
import app.logdate.wear.di.wearAudioModule
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

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
        
        // Initialize Napier for logging
        Napier.base(DebugAntilog())
        Napier.d("LogDate Wear OS application starting")
        
        // Initialize Koin
        startKoin {
            androidLogger(Level.ERROR) // Minimize logging overhead on Wear OS
            androidContext(this@LogDateWearApplication)
            modules(
                wearAudioModule,
                // Add more modules as needed
            )
        }
        
        Napier.d("LogDate Wear OS application initialized")
    }
}