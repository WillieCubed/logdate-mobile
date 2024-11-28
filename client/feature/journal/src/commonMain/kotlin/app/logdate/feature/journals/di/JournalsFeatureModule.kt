package app.logdate.feature.journals.di

import app.logdate.feature.journals.ui.JournalsOverviewViewModel
import app.logdate.feature.journals.ui.creation.JournalCreationViewModel
import app.logdate.feature.journals.ui.detail.JournalDetailViewModel
import app.logdate.feature.journals.ui.settings.JournalSettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Module for Journals functionality.
 */
val journalsFeatureModule: Module = module {
    viewModel { JournalsOverviewViewModel(get()) }
    viewModel { JournalCreationViewModel(get(), get()) }
    viewModel { JournalDetailViewModel(get(), get(), get()) }
    viewModel { JournalSettingsViewModel(get(), get(), get()) }
}