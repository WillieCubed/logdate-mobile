package app.logdate.feature.editor.di

import app.logdate.client.domain.di.domainModule
import app.logdate.feature.editor.ui.audio.AudioViewModel
import app.logdate.feature.editor.ui.audio.audioModule
import app.logdate.feature.editor.ui.camera.CameraViewModel
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
import app.logdate.feature.editor.ui.editor.delegate.AudioBlockFinalizer
import app.logdate.feature.editor.ui.editor.delegate.ContentLoader
import app.logdate.feature.editor.ui.editor.delegate.DefaultAudioBlockFinalizer
import app.logdate.feature.editor.ui.editor.delegate.DraftManager
import app.logdate.feature.editor.ui.editor.delegate.PendingAudioResolver
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin dependency injection module for the editor feature.
 *
 * This module provides all dependencies needed by the editor screens, including ViewModels,
 * use cases, and platform-specific services.
 */
val editorFeatureModule: Module =
    module {
        includes(domainModule)
        includes(platformEditorModule)
        includes(audioModule)

        factoryOf(::DraftManager)
        factoryOf(::ContentLoader)

        // Bind the AudioViewModel-backed PendingAudioResolver so the editor's save
        // path can absorb a recording whose URI is still in flight on the audio side.
        factory<PendingAudioResolver> { get<AudioViewModel>() }
        factory<AudioBlockFinalizer> { DefaultAudioBlockFinalizer(resolver = get()) }

        viewModel {
            CameraViewModel(
                cameraCaptureManager = get(),
            )
        }

        viewModel {
            EntryEditorViewModel(
                observeEditorData = get(),
                saveEntryUseCase = get(),
                draftManager = get(),
                contentLoader = get(),
                audioBlockFinalizer = get(),
            )
        }
    }
