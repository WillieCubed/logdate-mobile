package app.logdate.feature.location.timeline.di

import app.logdate.client.domain.location.history.SuggestNearbyHistoryPlacesUseCase
import app.logdate.feature.location.timeline.ui.LocationTimelineViewModel
import app.logdate.feature.location.timeline.ui.history.HumanLocationHistoryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val locationTimelineModule =
    module {
        single { SuggestNearbyHistoryPlacesUseCase(get()) }
        viewModel { HumanLocationHistoryViewModel(get(), get(), get(), get(), get()) }
        viewModel { LocationTimelineViewModel(get(), get(), get(), get(), get(), get()) }
    }
