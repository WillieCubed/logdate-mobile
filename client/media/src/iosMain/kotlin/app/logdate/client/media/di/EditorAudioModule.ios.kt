package app.logdate.client.media.di

import app.logdate.client.media.audio.EditorAudioRecorder
import app.logdate.client.media.audio.EditorAudioRecorderConfig
import app.logdate.client.media.audio.MockEditorAudioRecorder
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS implementation of the EditorAudioModule
 */
actual val editorAudioModule: Module = module {
    includes(commonEditorAudioModule)
    
    // Provide the mock implementation for iOS
    single<EditorAudioRecorder> { 
        MockEditorAudioRecorder(
            config = EditorAudioRecorderConfig()
        ) 
    }
}