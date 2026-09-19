@file:Suppress("ktlint:standard:filename")

package app.logdate

import androidx.compose.ui.window.application
import app.logdate.desktop.LogDateApplication
import app.logdate.desktop.rememberApplicationState
import app.logdate.di.appModule
import coil3.SingletonImageLoader
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

/**
 * Launches the LogDate application.
 */
fun main() =
    application {
        SingletonImageLoader.setSafe { context -> buildLogDateImageLoader(context) }
        KoinApplication(koinConfiguration { modules(appModule) }) {
            Napier.base(DebugAntilog())
            LogDateApplication(rememberApplicationState())
        }
    }
