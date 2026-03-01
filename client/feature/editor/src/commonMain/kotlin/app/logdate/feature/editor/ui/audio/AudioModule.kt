package app.logdate.feature.editor.ui.audio

import app.logdate.feature.editor.audio.AudioContextProcessor
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Koin module for audio operations.
 * This module provides the unified AudioViewModel that manages both recording and playback.
 * The actual platform-specific implementations are provided by the app-level audio module.
 */
val audioModule: Module = module {
    // AudioPlaybackManager and AudioRecordingManager are provided by the app-level audioModule.
    
    // Unified AudioViewModel - now all dependencies are properly registered
    viewModelOf(::AudioViewModel)

    // Audio context processing (waveforms, segments, palettes)
    factoryOf(::AudioContextProcessor)
}
