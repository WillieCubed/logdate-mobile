package app.logdate.feature.editor.di

import app.logdate.client.domain.di.domainModule
import app.logdate.feature.editor.ui.audio.audioModule
import app.logdate.feature.editor.ui.editor.EntryEditorViewModel
import app.logdate.feature.editor.ui.editor.delegate.AutoSaveDelegate
import app.logdate.feature.editor.ui.editor.delegate.JournalSelectionDelegate
import app.logdate.feature.editor.ui.editor.mediator.EditorMediator
import app.logdate.feature.editor.ui.editor.mediator.EditorMediatorImpl
import app.logdate.feature.editor.ui.image.ImageBlockViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * A module that provides the dependencies for the editor feature.
 */
val editorFeatureModule: Module = module {
    includes(domainModule)
    includes(platformEditorModule)
    includes(audioModule)
    
    // AudioRecordingManager, AudioPlaybackManager, and ImagePickerService are provided by platformEditorModule
    
    // Provide mediator as a singleton to ensure consistent state across components
    singleOf(::EditorMediatorImpl) bind EditorMediator::class
    
    // Provide delegates as factories (new instance created each time)
    factoryOf(::AutoSaveDelegate)
    factoryOf(::JournalSelectionDelegate)

    
    // Audio ViewModel is now provided by the audioModule()
    
    // Image block view model
    viewModel {
        ImageBlockViewModel(
            imagePickerService = get()
        )
    }

    viewModel { 
        // Provide all required dependencies for fully functional entry editor
        EntryEditorViewModel(
            fetchTodayNotes = get(),
            getCurrentUserJournals = get(),
            getDefaultSelectedJournals = get(),
            addNoteUseCase = get(),
            journalContentRepository = get(),
//            observeLocation = get(),
            updateEntryDraft = get(),
            createEntryDraft = get(),
            deleteEntryDraft = get(),
            fetchEntryDraft = get(),
            fetchMostRecentDraft = get(),
            getAllDrafts = get(),
            // Add mediator and delegates
            mediator = get(),
            autoSaveDelegate = get(),
            journalSelectionDelegate = get()
        )
    }
}