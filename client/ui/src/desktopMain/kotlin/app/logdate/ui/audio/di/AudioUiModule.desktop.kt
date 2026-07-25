package app.logdate.ui.audio.di

import app.logdate.ui.audio.DesktopWaveformStorage
import app.logdate.ui.audio.WaveformStorage
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

actual val audioUiModule: Module =
    module {
        single<WaveformStorage> {
            val cacheDir = File(System.getProperty("user.home"), ".logdate/cache").apply { mkdirs() }
            DesktopWaveformStorage(cacheDir)
        }
    }
