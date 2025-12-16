package app.logdate.feature.location.timeline.di

import app.logdate.feature.location.timeline.ui.LocationTimelineViewModel
import org.koin.core.module.dsl.viewModel

import org.koin.dsl.module

val locationTimelineModule = module {
    viewModel { LocationTimelineViewModel(get(), get(), get(), get(), get(), get()) }
}