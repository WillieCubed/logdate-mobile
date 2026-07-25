package app.logdate.ui.audio.di

import app.logdate.ui.audio.AndroidWaveformStorage
import app.logdate.ui.audio.WaveformStorage
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val audioUiModule: Module =
    module {
        single<WaveformStorage> { AndroidWaveformStorage(androidContext()) }
    }
