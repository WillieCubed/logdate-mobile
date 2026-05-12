package app.logdate.client.intelligence.di

import app.logdate.client.intelligence.curation.MediaSignalExtractor
import app.logdate.client.intelligence.curation.NoSignalsExtractor
import org.koin.core.module.Module
import org.koin.dsl.module

actual val curationModule: Module =
    module {
        // Desktop image-header reading (ImageIO) lands later; until then the curator
        // runs with only the cross-platform behavioral signals on JVM.
        single<MediaSignalExtractor> { NoSignalsExtractor() }
    }
