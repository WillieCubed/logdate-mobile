package app.logdate.client.e2e

import app.logdate.client.datastore.SessionStorage
import app.logdate.client.device.identity.CanonicalOwnerProvider
import app.logdate.client.domain.restore.RestoreUserDataUseCase
import app.logdate.client.media.MediaManager
import app.logdate.feature.core.restore.RestoreDiagnosticEvents
import app.logdate.feature.core.restore.RestoreLauncher
import app.logdate.shared.config.LogDateConfigRepository
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module

/** Keeps application observers alive while a worker receives test dependencies. */
internal class RestoreWorkerTestBindings {
    private val koin = GlobalContext.get()
    private val useCase = koin.get<RestoreUserDataUseCase>()
    private val media = koin.get<MediaManager>()
    private val launcher = koin.get<RestoreLauncher>()
    private val owner = koin.get<CanonicalOwnerProvider>()
    private val config = koin.get<LogDateConfigRepository>()
    private val sessions = koin.get<SessionStorage>()
    private val diagnostics = koin.getOrNull<RestoreDiagnosticEvents>()

    fun restore() {
        loadKoinModules(
            module {
                factory { useCase }
                single<MediaManager> { media }
                single<RestoreLauncher> { launcher }
                single<CanonicalOwnerProvider> { owner }
                single<LogDateConfigRepository> { config }
                single<SessionStorage> { sessions }
                diagnostics?.let { original -> single<RestoreDiagnosticEvents> { original } }
            },
        )
    }
}
