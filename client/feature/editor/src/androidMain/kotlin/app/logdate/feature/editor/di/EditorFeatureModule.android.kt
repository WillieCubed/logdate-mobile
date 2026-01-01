package app.logdate.feature.editor.di

import app.logdate.feature.editor.ui.audio.AndroidAudioPlaybackManager
import app.logdate.feature.editor.ui.audio.AndroidAudioRecordingManager
import app.logdate.feature.editor.ui.audio.AudioPlaybackManager
import app.logdate.feature.editor.ui.audio.AudioRecordingManager
import app.logdate.feature.editor.ui.camera.AndroidCameraCaptureManager
import app.logdate.feature.editor.ui.camera.CameraCaptureManager
import app.logdate.feature.editor.ui.image.AndroidImagePickerService
import app.logdate.feature.editor.ui.image.ImagePickerService
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-specific module for the editor feature.
 * Provides platform-specific implementations.
 */
actual val platformEditorModule: Module = module {
    // Provide Android implementation of AudioRecordingManager
    single<AudioRecordingManager> {
        AndroidAudioRecordingManager(androidContext())
    }

    // Provide Android implementation of AudioPlaybackManager
    single<AudioPlaybackManager> {
        AndroidAudioPlaybackManager(androidContext())
    }

    // Provide Android implementation of ImagePickerService
    single<ImagePickerService> {
        AndroidImagePickerService(androidContext())
    }

    // Provide Android implementation of CameraCaptureManager
    single<CameraCaptureManager> {
        AndroidCameraCaptureManager(androidContext())
    }
}