package app.logdate.feature.library.di

import app.logdate.feature.library.ui.LibraryViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin module for Library feature dependencies.
 */
val libraryFeatureModule: Module =
    module {
        viewModel { LibraryViewModel(notesRepository = get()) }
    }
