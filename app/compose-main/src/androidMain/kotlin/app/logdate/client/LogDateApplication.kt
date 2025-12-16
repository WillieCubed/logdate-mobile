package app.logdate.client

import android.app.Application
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

        Napier.base(DebugAntilog())
        initializeKoin()
    }
}
