package app.logdate.feature.editor.di

import app.logdate.feature.editor.ui.audio.AudioPlaybackManager
import app.logdate.feature.editor.ui.audio.AudioRecordingManager
import app.logdate.feature.editor.ui.audio.IosAudioPlaybackManager
import app.logdate.feature.editor.ui.audio.IosAudioRecordingManager
import app.logdate.feature.editor.ui.image.IosImagePickerService
import app.logdate.feature.editor.ui.image.ImagePickerService
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS-specific module for the editor feature.
 * Provides platform-specific implementations.
 */
actual val platformEditorModule: Module = module {
    // Provide iOS implementation of AudioRecordingManager
    factory<AudioRecordingManager> {
        IosAudioRecordingManager()
    }
    
    // Provide iOS implementation of AudioPlaybackManager
    factory<AudioPlaybackManager> {
        IosAudioPlaybackManager()
    }
    
    // Provide iOS implementation of ImagePickerService
    factory<ImagePickerService> {
        IosImagePickerService()
    }
}