package app.logdate.client.media.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Common audio module components
 */
val commonEditorAudioModule = module {

}

/**
 * Module that provides the EditorAudioRecorder implementation
 */
expect val editorAudioModule: Module