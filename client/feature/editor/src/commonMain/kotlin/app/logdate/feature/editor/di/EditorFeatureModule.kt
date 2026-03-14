package app.logdate.feature.editor.di

import app.logdate.client.domain.di.domainModule
import app.logdate.feature.editor.ui.audio.audioModule
import app.logdate.feature.editor.ui.camera.CameraViewModel
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
import app.logdate.feature.editor.ui.editor.delegate.AutoSaveDelegate
import app.logdate.feature.editor.ui.editor.delegate.JournalSelectionDelegate
import app.logdate.feature.editor.ui.editor.mediator.EditorMediator
import app.logdate.feature.editor.ui.editor.mediator.EditorMediatorImpl
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Koin dependency injection module for the editor feature.
 *
 * This module provides all dependencies needed by the editor screens, including ViewModels,
 * use cases, and platform-specific services. Dependencies are organized by layer:
 *
 * - domainModule: Provides use cases and domain logic, including FetchEntryUseCase which enables
 *   the multi-window editing feature by loading entries by ID
 * - platformEditorModule: Provides platform-specific services (camera, playback, waveform storage)
 * - audioModule: Provides audio ViewModels and UI state wiring
 *
 * ViewModels provided:
 * - EntryEditorViewModel: Main editor state and operations (note creation, editing, saving)
 * - CameraViewModel: Handles camera capture functionality
 *
 * Other dependencies:
 * - EditorMediator: Acts as a mediator for editor component communication (singleton)
 * - AutoSaveDelegate: Handles automatic saving of drafts (factory-scoped)
 * - JournalSelectionDelegate: Manages journal selection and default journal logic (factory-scoped)
 *
 * Architecture note: The editor feature depends only on the domain layer (use cases) and
 * platform-specific modules, never directly on the data layer. This maintains clean architecture
 * separation of concerns. FetchEntryUseCase in particular demonstrates this pattern by accessing
 * entries through domain layer abstractions rather than data layer DAOs.
 */
val editorFeatureModule: Module =
    module {
        includes(domainModule)
        includes(platformEditorModule)
        includes(audioModule)

        // AudioRecordingManager is provided by client.media audioModule at the app level.
        // AudioPlaybackManager is provided by the app-level audioModule.
        // Provide mediator as a singleton to ensure consistent state across components
        singleOf(::EditorMediatorImpl) bind EditorMediator::class

        // Provide delegates as factories (new instance created each time)
        factoryOf(::AutoSaveDelegate)
        factoryOf(::JournalSelectionDelegate)

        // Audio ViewModel is now provided by the audioModule()

        // Camera view model
        viewModel {
            CameraViewModel(
                cameraCaptureManager = get(),
            )
        }

        viewModel {
            // Provide all required dependencies for fully functional entry editor
            EntryEditorViewModel(
                fetchTodayNotes = get(),
                getCurrentUserJournals = get(),
                getDefaultSelectedJournals = get(),
                addNoteUseCase = get(),
                fetchEntryUseCase = get(),
                journalContentRepository = get(),
//            observeLocation = get(),
                updateEntryDraft = get(),
                createEntryDraft = get(),
                deleteEntryDraft = get(),
                deleteAllDraftsUseCase = get(),
                fetchEntryDraft = get(),
                fetchMostRecentDraft = get(),
                getAllDrafts = get(),
                cleanupExpiredDrafts = get(),
                // Add mediator and delegates
                mediator = get(),
                autoSaveDelegate = get(),
                journalSelectionDelegate = get(),
            )
        }
    }
