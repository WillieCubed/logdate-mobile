package app.logdate.feature.editor.di

import app.logdate.feature.editor.audio.extraction.AmplitudeExtractor
import app.logdate.feature.editor.audio.extraction.DesktopAmplitudeExtractor
import app.logdate.feature.editor.audio.storage.DesktopWaveformStorage
import app.logdate.feature.editor.audio.storage.WaveformStorage
import app.logdate.feature.editor.ui.camera.CameraCaptureManager
import app.logdate.feature.editor.ui.camera.DesktopCameraCaptureManager
import app.logdate.feature.editor.ui.image.DesktopImagePickerService
import app.logdate.feature.editor.ui.image.ImagePickerService
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/**
 * Desktop-specific module for the editor feature.
 * Provides platform-specific implementations.
 */
actual val platformEditorModule: Module = module {
    // Provide Desktop implementation of ImagePickerService
    factory<ImagePickerService> {
        DesktopImagePickerService()
    }

    // Provide Desktop stub implementation of CameraCaptureManager
    factory<CameraCaptureManager> {
        DesktopCameraCaptureManager()
    }

    // Audio waveform processing dependencies
    single<AmplitudeExtractor> {
        DesktopAmplitudeExtractor()
    }
    single<WaveformStorage> {
        val cacheDir = File(System.getProperty("user.home"), ".logdate/cache").apply { mkdirs() }
        DesktopWaveformStorage(cacheDir)
    }
}
