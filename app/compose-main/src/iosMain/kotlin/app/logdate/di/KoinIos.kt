package app.logdate.di

import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.core.logger.PrintLogger
import org.koin.mp.KoinPlatformTools

/**
 * Initializes Koin on iOS.
 *
 * SwiftUI does not automatically create a Koin context, so expose a simple
 * entry point to be called from the iOS app lifecycle.
 */
fun initKoinIos() {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) {
        return
    }
    startKoin {
        logger(PrintLogger(Level.ERROR))
        modules(appModule)
    }
}
