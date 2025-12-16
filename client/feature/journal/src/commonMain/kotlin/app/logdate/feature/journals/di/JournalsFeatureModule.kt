package app.logdate.feature.journals.di

import app.logdate.client.sharing.di.sharingModule
import app.logdate.feature.journals.ui.JournalsOverviewViewModel
import app.logdate.feature.journals.ui.creation.JournalCreationViewModel
import app.logdate.feature.journals.ui.detail.JournalDetailViewModel
import app.logdate.feature.journals.ui.detail.NoteDetailViewModel
import app.logdate.feature.journals.ui.settings.JournalSettingsViewModel
import app.logdate.feature.journals.ui.share.ShareJournalViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Module for Journals functionality.
 */
val journalsFeatureModule: Module = module {
    includes(sharingModule)
    viewModel { JournalsOverviewViewModel(get()) }
    viewModel { JournalCreationViewModel(get(), get()) }
    viewModel {
        JournalDetailViewModel(
            repository = get(),
            sharingLauncher = get(),
            journalContentRepository = get(),
            savedStateHandle = get(),
        )
    }
    viewModel { NoteDetailViewModel(get(), get(), get()) }
    viewModel { 
        JournalSettingsViewModel(
            getJournalByIdUseCase = get(),
            updateJournalUseCase = get(),
            deleteJournalUseCase = get(),
            sharingLauncher = get()
        ) 
    }
    viewModel { ShareJournalViewModel(get(), get()) }
}