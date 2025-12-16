package app.logdate.client.media.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android implementation of the EditorAudioModule
 */
actual val editorAudioModule: Module = module {
    includes(commonEditorAudioModule)
    

}