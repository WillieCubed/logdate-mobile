package app.logdate

import androidx.compose.ui.window.application
import app.logdate.desktop.LogDateApplication
import app.logdate.desktop.rememberApplicationState
import app.logdate.di.appModule
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.compose.KoinApplication

/**
 * Launches the LogDate application.
 */
fun main() = application {
    KoinApplication(application = {
        modules(appModule)
    }) {
        Napier.base(DebugAntilog())
        LogDateApplication(rememberApplicationState())
    }
}
