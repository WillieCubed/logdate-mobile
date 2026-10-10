@file:Suppress("ktlint:standard:filename")

package app.logdate

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.application
import app.logdate.client.data.notes.StoredMediaReferenceMigrationLauncher
import app.logdate.client.media.storage.desktopMediaFileResolver
import app.logdate.desktop.LogDateApplication
import app.logdate.desktop.rememberApplicationState
import app.logdate.di.appModule
import coil3.SingletonImageLoader
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.koinConfiguration

/**
 * Launches the LogDate application.
 */
fun main() =
    application {
        SingletonImageLoader.setSafe { context -> buildLogDateImageLoader(context, desktopMediaFileResolver()) }
        KoinApplication(koinConfiguration { modules(appModule) }) {
            val console = DebugAntilog()
            Napier.base(
                app.logdate.client.sync.diagnostics.PrivateLocalAntilog { priority, message ->
                    console.log(priority, "LogDate", null, message)
                },
            )
            val mediaReferenceMigration = koinInject<StoredMediaReferenceMigrationLauncher>()
            LaunchedEffect(mediaReferenceMigration) { mediaReferenceMigration.start() }
            LogDateApplication(rememberApplicationState())
        }
    }
