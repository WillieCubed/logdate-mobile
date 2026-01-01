package app.logdate.feature.editor.di

import app.logdate.feature.editor.ui.audio.AudioPlaybackManager
import app.logdate.feature.editor.ui.audio.AudioRecordingManager
import app.logdate.feature.editor.ui.audio.DesktopAudioPlaybackManager
import app.logdate.feature.editor.ui.audio.DesktopAudioRecordingManager
import app.logdate.feature.editor.ui.camera.CameraCaptureManager
import app.logdate.feature.editor.ui.camera.DesktopCameraCaptureManager
import app.logdate.feature.editor.ui.image.DesktopImagePickerService
import app.logdate.feature.editor.ui.image.ImagePickerService
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop-specific module for the editor feature.
 * Provides platform-specific implementations.
 */
actual val platformEditorModule: Module = module {
    // Provide Desktop implementation of AudioRecordingManager
    factory<AudioRecordingManager> {
        DesktopAudioRecordingManager()
    }

    // Provide Desktop implementation of AudioPlaybackManager
    factory<AudioPlaybackManager> {
        DesktopAudioPlaybackManager()
    }

    // Provide Desktop implementation of ImagePickerService
    factory<ImagePickerService> {
        DesktopImagePickerService()
    }

    // Provide Desktop stub implementation of CameraCaptureManager
    factory<CameraCaptureManager> {
        DesktopCameraCaptureManager()
    }
}