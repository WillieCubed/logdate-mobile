package app.logdate.feature.editor.di

import app.logdate.client.media.MediaManager
import app.logdate.feature.editor.audio.extraction.AmplitudeExtractor
import app.logdate.feature.editor.audio.extraction.AndroidAmplitudeExtractor
import app.logdate.feature.editor.ui.camera.AndroidCameraCaptureManager
import app.logdate.feature.editor.ui.camera.CameraCaptureManager
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-specific module for the editor feature.
 * Provides platform-specific implementations.
 */
actual val platformEditorModule: Module =
    module {
        // Provide Android implementation of CameraCaptureManager.
        // Factory-scoped so each CameraViewModel gets a fresh instance with clean lifecycle state.
        factory<CameraCaptureManager> {
            AndroidCameraCaptureManager(androidContext(), get<MediaManager>(), get<CoroutineScope>())
        }

        // Audio waveform processing dependencies
        single<AmplitudeExtractor> {
            AndroidAmplitudeExtractor(androidContext())
        }
    }
