package app.logdate.feature.events.di

import app.logdate.feature.events.ui.EventDetailViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val eventsFeatureModule: Module =
    module {
        viewModelOf(::EventDetailViewModel)
    }
