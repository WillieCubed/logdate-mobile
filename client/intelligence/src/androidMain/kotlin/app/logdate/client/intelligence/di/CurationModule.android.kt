package app.logdate.client.intelligence.di

import app.logdate.client.intelligence.curation.AndroidMediaSignalExtractor
import app.logdate.client.intelligence.curation.MediaSignalExtractor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val curationModule: Module =
    module {
        single<MediaSignalExtractor> {
            AndroidMediaSignalExtractor(contentResolver = androidContext().contentResolver)
        }
    }
