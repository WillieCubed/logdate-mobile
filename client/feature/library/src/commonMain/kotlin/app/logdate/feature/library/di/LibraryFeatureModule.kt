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
        viewModel { LibraryViewModel(notesRepository = get(), indexedMediaRepository = get()) }
        viewModel { (mediaId: Uuid) ->
            MediaDetailViewModel(
                mediaId = mediaId,
                notesRepository = get(),
                journalContentRepository = get(),
                indexedMediaRepository = get(),
                resolveLocationToPlaceUseCase = get(),
                remoteDisplayManager = get(),
            )
        }
    }
