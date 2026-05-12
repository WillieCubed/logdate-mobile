package app.logdate.feature.editor.di

import app.logdate.feature.editor.audio.extraction.AmplitudeExtractor
import app.logdate.feature.editor.audio.extraction.IosAmplitudeExtractor
import app.logdate.feature.editor.audio.storage.IosWaveformStorage
import app.logdate.feature.editor.audio.storage.WaveformStorage
import app.logdate.feature.editor.ui.camera.CameraCaptureManager
import app.logdate.feature.editor.ui.camera.IosCameraCaptureManager
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS-specific module for the editor feature.
 * Provides platform-specific implementations.
 */
actual val platformEditorModule: Module =
    module {
        // Provide iOS capability fallback of CameraCaptureManager
        factory<CameraCaptureManager> {
            IosCameraCaptureManager()
        }

        // Audio waveform processing dependencies
        single<AmplitudeExtractor> {
            IosAmplitudeExtractor()
        }
        single<WaveformStorage> {
            IosWaveformStorage()
        }
    }
