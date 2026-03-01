package app.logdate.client

import android.app.Application
import android.util.Log
import app.logdate.di.initializeKoin
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.core.component.KoinComponent

/**
 * Application class for the LogDate Android app.
 *
 * For app UI, see [MainActivity].
 */
class LogdateApplication : Application(), KoinComponent {

    override fun onCreate() {
        super.onCreate()
        Log.i(APP_STARTUP_TAG, "Application onCreate: initializing logging and DI")

        Napier.base(DebugAntilog())
        initializeKoin()
        Log.i(APP_STARTUP_TAG, "Application onCreate: Koin initialized")
    }
}

private const val APP_STARTUP_TAG = "LogDateStartup"
