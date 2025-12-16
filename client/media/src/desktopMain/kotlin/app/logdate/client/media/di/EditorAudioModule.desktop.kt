package app.logdate.client.media.di

import app.logdate.client.media.audio.DesktopEditorAudioRecorder
import app.logdate.client.media.audio.EditorAudioRecorder
import app.logdate.client.media.audio.EditorAudioRecorderConfig
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Desktop implementation of the EditorAudioModule
 */
actual val editorAudioModule: Module = module {
    includes(commonEditorAudioModule)
    
    // Provide the real implementation for desktop using JavaSound API
    single<EditorAudioRecorder> { 
        DesktopEditorAudioRecorder(
            config = EditorAudioRecorderConfig()
        ) 
    }
}