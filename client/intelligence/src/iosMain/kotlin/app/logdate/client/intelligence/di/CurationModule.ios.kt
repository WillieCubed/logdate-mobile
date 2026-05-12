package app.logdate.client.intelligence.di

import app.logdate.client.intelligence.curation.MediaSignalExtractor
import app.logdate.client.intelligence.curation.NoSignalsExtractor
import org.koin.core.module.Module
import org.koin.dsl.module

actual val curationModule: Module =
    module {
        // PHAsset-backed extractor lands later; until then the curator runs with only
        // the cross-platform behavioral signals on iOS.
        single<MediaSignalExtractor> { NoSignalsExtractor() }
    }
