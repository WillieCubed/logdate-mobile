package app.logdate.feature.editor.ui.audio

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin module for audio operations.
 * This module provides the unified AudioViewModel that manages both recording and playback.
 * The actual platform-specific implementations are provided by the platformEditorModule.
 */
val audioModule: Module = module {
    // Both AudioPlaybackManager and AudioRecordingManager are provided by platformEditorModule
    
    // Unified AudioViewModel - now all dependencies are properly registered
    viewModelOf(::AudioViewModel)
}