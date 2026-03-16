package app.logdate.feature.library.di

import app.logdate.feature.library.ui.LibraryViewModel
import app.logdate.feature.library.ui.detail.MediaDetailViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.uuid.Uuid

/**
 * Koin module for Library feature dependencies.
 */
val libraryFeatureModule: Module =
    module {
        viewModel { LibraryViewModel(notesRepository = get()) }
        viewModel { (noteId: Uuid) ->
            MediaDetailViewModel(
                noteId = noteId,
                notesRepository = get(),
                journalContentRepository = get(),
                indexedMediaRepository = get(),
            )
        }
    }
