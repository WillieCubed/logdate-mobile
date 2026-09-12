package app.logdate.server.di

import app.logdate.server.entitlements.entitlementsModule
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import org.koin.core.context.stopKoin
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

/**
 * Installs the Koin container with every server module, and tears it down when the application
 * stops so the next test application starts from a clean state.
 */
internal fun Application.installServerKoin(isDatabaseAvailable: Boolean) {
    // Stop any existing Koin instance to ensure clean state for tests
    runCatching { stopKoin() }

    install(Koin) {
        slf4jLogger()
        modules(
            serverModule(isDatabaseAvailable),
            entitlementsModule(databaseAvailable = isDatabaseAvailable),
        )
    }

    monitor.subscribe(ApplicationStopped) {
        runCatching { stopKoin() }
    }
}
