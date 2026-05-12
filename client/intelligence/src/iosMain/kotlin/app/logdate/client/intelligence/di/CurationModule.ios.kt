package app.logdate.client.intelligence.di

import app.logdate.client.intelligence.curation.IosMediaSignalExtractor
import app.logdate.client.intelligence.curation.MediaSignalExtractor
import org.koin.core.module.Module
import org.koin.dsl.module

actual val curationModule: Module =
    module {
        single<MediaSignalExtractor> { IosMediaSignalExtractor() }
    }
